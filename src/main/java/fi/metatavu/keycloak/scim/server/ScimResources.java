package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.consts.ContentTypes;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilterParser;
import fi.metatavu.keycloak.scim.server.metadata.MetadataController;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.model.UsersList;
import fi.metatavu.keycloak.scim.server.users.UsersController;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;
import org.keycloak.common.ClientConnection;
import org.keycloak.models.*;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.AppAuthManager;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * SCIM REST resources
 */
public class ScimResources {

    private static final Logger logger = Logger.getLogger(ScimResources.class.getName());
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ScimResources.class);
    private final UsersController usersController;
    private final MetadataController metadataController;
    private final ScimFilterParser scimFilterParser;

    ScimResources() {
        usersController = new UsersController();
        metadataController = new MetadataController();
        scimFilterParser = new ScimFilterParser();
    }

    @POST
    @Path("v2/Users")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response createUser(
            @Context KeycloakSession session,
            fi.metatavu.keycloak.scim.server.model.User scimUser
    ) {
        verifyPermissions(session);

        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            throw new NotFoundException("Realm not found");
        }

        if (scimUser.getUserName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing userName").build();
        }

        UserModel existing = session.users().getUserByUsername(realm, scimUser.getUserName());
        if (existing != null) {
            return Response.status(Response.Status.CONFLICT).entity("User already exists").build();
        }

        ScimContext scimContext = getScimContext(session);
        User user = usersController.createUser(scimContext, scimUser);
        URI location = UriBuilder.fromPath("v2/Users/{id}").build(user.getId());

        return Response
            .created(location)
            .entity(user)
            .build();
    }

    @GET
    @Path("v2/Users")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listUsers(
        @Context KeycloakSession session,
        @QueryParam("filter") String filter,
        @QueryParam("startIndex") @DefaultValue("0") Integer startIndex,
        @QueryParam("count") @DefaultValue("100") Integer count
    ) {
        verifyPermissions(session);

        ScimFilter scimFilter;
        try {
            scimFilter = parseFilter(filter);
        } catch (Exception e) {
            logger.warn(String.format("Failed to parse filter: '%s'", filter), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid filter").build();
        }

        UsersList usersList = usersController.listUsers(
            getScimContext(session),
            scimFilter,
            startIndex,
            count
        );

        return Response.ok(usersList).build();
    }

    @GET
    @Path("v2/Users/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response getUser(
            @Context KeycloakSession session,
            @PathParam("id") String userId
    ) {
        verifyPermissions(session);

        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            throw new NotFoundException("Realm not found");
        }

        User user = usersController.findUser(getScimContext(session), userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(user).build();
    }

    @PUT
    @Path("v2/Users/{id}")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response updateUser(
            @Context KeycloakSession session,
            @PathParam("id") String userId,
            fi.metatavu.keycloak.scim.server.model.User scimUser
    ) {
        verifyPermissions(session);

        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            logger.warn("Realm not found");
            throw new NotFoundException("Realm not found");
        }

        if (scimUser.getUserName().isBlank()) {
            logger.warn("Missing userName");
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing userName").build();
        }

        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }

        // Check if username is being changed to an already existing one
        UserModel existing = session.users().getUserByUsername(realm, scimUser.getUserName());
        if (existing == null) {
            logger.warn(String.format("User not found: %s", scimUser.getUserName()));
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }

        if (!existing.getId().equals(userId)) {
            logger.warn(String.format("User name already taken: %s", scimUser.getUserName()));
            return Response.status(Response.Status.CONFLICT).entity("User name already taken").build();
        }

        ScimContext scimContext = getScimContext(session);
        User result = usersController.updateUser(scimContext, existing, scimUser);

        return Response.ok(result).build();
    }

    @DELETE
    @Path("v2/Users/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response deleteUser(
            @Context KeycloakSession session,
            @PathParam("id") String userId
    ) {
        verifyPermissions(session);

        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            throw new NotFoundException("Realm not found");
        }

        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        RoleModel scimManagedRole = realm.getRole("scim-managed");
        if (scimManagedRole != null && !user.hasRole(scimManagedRole)) {
            logger.warn(String.format("User is not SCIM-managed: %s", userId));
            return Response.status(Response.Status.FORBIDDEN).entity("User is not managed by SCIM").build();
        }

        session.users().removeUser(realm, user);

        return Response.noContent().build();
    }

    @GET
    @Path("v2/ResourceTypes")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listResourceTypes(
            @Context KeycloakSession session,
            @Context UriInfo uriInfo
    ) {
        verifyPermissions(session);

        KeycloakContext context = session.getContext();
        if (context == null) {
            logger.warn("Keycloak context not found");
            throw new InternalServerErrorException("Keycloak context not found");
        }

        RealmModel realm = context.getRealm();
        if (realm == null) {
            logger.warn("Realm not found");
            throw new NotFoundException("Realm not found");
        }

        ScimContext scimContext = getScimContext(session);

        return Response.ok(metadataController.getResourceTypes(scimContext)).build();
    }

    @GET
    @Path("v2/Schemas")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listSchemas(
            @Context KeycloakSession session,
            @Context UriInfo uriInfo
    ) {
        verifyPermissions(session);

        KeycloakContext context = session.getContext();
        if (context == null) {
            logger.warn("Keycloak context not found");
            throw new InternalServerErrorException("Keycloak context not found");
        }

        RealmModel realm = context.getRealm();
        if (realm == null) {
            logger.warn("Realm not found");
            throw new NotFoundException("Realm not found");
        }

        ScimContext scimContext = getScimContext(session);

        return Response.ok(metadataController.listSchemas(scimContext)).build();
    }

    @GET
    @Path("v2/ServiceProviderConfig")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response getServiceProviderConfig(
            @Context KeycloakSession session,
            @Context UriInfo uriInfo
    ) {
        verifyPermissions(session);

        KeycloakContext context = session.getContext();
        if (context == null) {
            logger.warn("Keycloak context not found");
            throw new InternalServerErrorException("Keycloak context not found");
        }

        RealmModel realm = context.getRealm();
        if (realm == null) {
            logger.warn("Realm not found");
            throw new NotFoundException("Realm not found");
        }

        ScimContext scimContext = getScimContext(session);

        return Response.ok(metadataController.getServiceProviderConfig(scimContext)).build();
    }

    /**
     * Verifies that the request has the required permission to access the resource
     *
     * @param session Keycloak session
     */
    private void verifyPermissions(KeycloakSession session) {
        KeycloakContext context = session.getContext();
        if (context == null) {
            logger.warn("Keycloak context not found");
            throw new InternalServerErrorException("Keycloak context not found");
        }

        RealmModel realm = context.getRealm();
        if (realm == null) {
            logger.warn("Realm not found");
            throw new NotFoundException("Realm not found");
        }

        HttpHeaders headers = context.getRequestHeaders();
        ClientConnection clientConnection = context.getConnection();

        AuthenticationManager.AuthResult auth = new AppAuthManager.BearerTokenAuthenticator(session)
                .setRealm(realm)
                .setConnection(clientConnection)
                .setHeaders(headers)
                .authenticate();

        if (auth == null || auth.getUser() == null || auth.getToken() == null) {
            logger.warn("Authentication failed");
            throw new NotAuthorizedException("Authentication failed");
        }

        ClientModel client = auth.getClient();
        if (client == null) {
            logger.warn("Client not found");
            throw new NotAuthorizedException("Client not found");
        }

        UserModel serviceAccount = session.users().getServiceAccount(client);

        RoleModel roleModel = realm.getRole(ScimRoles.SERVICE_ACCOUNT_ROLE);
        if (roleModel == null) {
            logger.warn("Service account role not configured");
            throw new ForbiddenException("Service account role not configured");
        }

        if (!hasServiceAccountRole(serviceAccount, roleModel)) {
            logger.warn("Service account does not have required role");
            throw new ForbiddenException("Service account does not have required role");
        }
    }

    /**
     * Parses SCIM filter
     *
     * @param filter filter
     * @return parsed filter or null if filter is not defined
     */
    private ScimFilter parseFilter(String filter) {
        if (filter != null && !filter.isBlank()) {
            return scimFilterParser.parse(filter);
        }

        return null;
    }

    /**
     * Checks if the service account has the required role
     *
     * @param user service account
     * @param serviceAccountRole service account role
     * @return true if the service account has the required role; false otherwise
     */
    private boolean hasServiceAccountRole(UserModel user, RoleModel serviceAccountRole) {
        return user != null && user.hasRole(serviceAccountRole);
    }

    /**
     * Returns SCIM context
     *
     * @param session Keycloak session
     * @return SCIM context
     */
    private ScimContext getScimContext(KeycloakSession session) {
        return new ScimContext(session.getContext().getUri().getBaseUri(), session, session.getContext().getRealm());
    }

}

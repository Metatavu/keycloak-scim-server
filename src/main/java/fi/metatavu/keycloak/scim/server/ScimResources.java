package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.consts.ContentTypes;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilterParser;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.model.UsersList;
import fi.metatavu.keycloak.scim.server.users.UsersController;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.jboss.logging.Logger;
import org.keycloak.common.ClientConnection;
import org.keycloak.models.*;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.AppAuthManager;

import java.net.URI;

/**
 * SCIM REST resources
 */
public class ScimResources {

    private static final Logger logger = Logger.getLogger(ScimResources.class.getName());
    private final UsersController usersController;
    private final ScimFilterParser scimFilterParser;

    ScimResources() {
        usersController = new UsersController();
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

        User user = usersController.createUser(session, realm, scimUser);

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
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid filter").build();
        }

        UsersList usersList = usersController.listUsers(
            session.getContext().getRealm(),
            session,
            scimFilter,
            startIndex,
            count
        );

        return Response.ok(usersList).build();
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
            logger.warnv("User not found: {}", userId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        RoleModel scimManagedRole = realm.getRole("scim-managed");
        if (scimManagedRole != null && !user.hasRole(scimManagedRole)) {
            logger.warnv("User is not SCIM-managed: {}", userId);
            return Response.status(Response.Status.FORBIDDEN).entity("User is not managed by SCIM").build();
        }

        session.users().removeUser(realm, user);
        logger.infov("Deleted SCIM user: {}", userId);

        return Response.noContent().build();
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

}

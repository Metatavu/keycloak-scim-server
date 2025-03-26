package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.consts.ContentTypes;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilterParser;
import fi.metatavu.keycloak.scim.server.model.UsersList;
import fi.metatavu.keycloak.scim.server.users.UsersController;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.common.ClientConnection;
import org.keycloak.models.*;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.AppAuthManager;

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

        org.keycloak.models.RoleModel roleModel = realm.getRole(ScimRoles.SERVICE_ACCOUNT_ROLE);
        if (roleModel == null) {
            logger.warn("Service account role not configured");
            throw new ForbiddenException("Service account role not configured");
        }

        if (!hasServiceAccountRole(serviceAccount, roleModel)) {
            logger.warn("Service account does not have required role");
            throw new ForbiddenException("Service account does not have required role");
        }

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
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    public Response deleteUser(@PathParam("id") String userId, @Context RealmModel realm, @Context KeycloakSession session) {
        // TODO: Implement user deletion
        return Response.noContent().build();
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

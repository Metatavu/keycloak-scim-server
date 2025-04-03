package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.authentication.ExternalTokenVerifier;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.groups.GroupsController;
import fi.metatavu.keycloak.scim.server.metadata.MetadataController;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import fi.metatavu.keycloak.scim.server.users.UsersController;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.common.ClientConnection;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.models.*;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Abstract SCIM server implementation
 *
 * @param <T> SCIM context type
 */
public abstract class AbstractScimServer <T extends ScimContext> implements ScimServer <T> {

    private static final Logger logger = Logger.getLogger(AbstractScimServer.class.getName());

    protected final MetadataController metadataController;
    protected final UsersController usersController;
    protected final GroupsController groupsController;

    /**
     * Constructor
     */
    public AbstractScimServer() {
        metadataController = new MetadataController();
        usersController = new UsersController();
        groupsController = new GroupsController();
    }

    @Override
    public Response updateUser(T scimContext, String userId, fi.metatavu.keycloak.scim.server.model.User updateRequest) {
        KeycloakSession session = scimContext.getSession();

        if (updateRequest.getUserName().isBlank()) {
            logger.warn("Missing userName");
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing userName").build();
        }

        RealmModel realm = scimContext.getRealm();
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }

        // Check if username is being changed to an already existing one
        UserModel existing = session.users().getUserByUsername(realm, updateRequest.getUserName());
        if (existing == null) {
            logger.warn(String.format("User not found: %s", updateRequest.getUserName()));
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }

        if (!existing.getId().equals(userId)) {
            logger.warn(String.format("User name already taken: %s", updateRequest.getUserName()));
            return Response.status(Response.Status.CONFLICT).entity("User name already taken").build();
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);
        fi.metatavu.keycloak.scim.server.model.User result = usersController.updateUser(scimContext, userAttributes, existing, updateRequest);

        return Response.ok(result).build();
    }

    @Override
    public Response patchUser(T scimContext, String userId, fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest) {
        KeycloakSession session = scimContext.getSession();

        RealmModel realm = scimContext.getRealm();
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }

        // Check if username is being changed to an already existing one
        UserModel existing = session.users().getUserById(realm, userId);
        if (existing == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        try {
            fi.metatavu.keycloak.scim.server.model.User result = usersController.patchUser(scimContext, userAttributes, existing, patchRequest);
            return Response.ok(result).build();
        } catch (UnsupportedPatchOperation e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Unsupported patch operation").build();
        }
    }

    @Override
    public Response listResourceTypes(T scimContext) {
        return Response.ok(metadataController.getResourceTypeList(scimContext)).build();
    }

    @Override
    public Response findResourceType(T scimContext, String id) {
        return Response.ok(metadataController.getResourceType(scimContext, id)).build();
    }

    @Override
    public Response listSchemas(T scimContext) {
        return Response.ok(metadataController.listSchemas(scimContext)).build();
    }

    @Override
    public Response findSchema(T scimContext, String id) {
        return Response.ok(metadataController.getSchema(scimContext, id)).build();
    }

    @Override
    public Response getServiceProviderConfig(T scimContext) {
        return Response.ok(metadataController.getServiceProviderConfig(scimContext)).build();
    }

    /**
     * Verifies that the request has the required permission to access the resource
     *
     * @param scimContext SCIM context
     */
    @Override
    public void verifyPermissions(T scimContext) {
        KeycloakSession session = scimContext.getSession();
        KeycloakContext context = session.getContext();
        ScimConfig config = scimContext.getConfig();

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
        String authorization = headers.getHeaderString(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            logger.warn("Missing or invalid Authorization header");
            throw new NotAuthorizedException("Missing or invalid Authorization header");
        }

        String tokenString = authorization.substring("Bearer ".length()).trim();

        if (config.getAuthenticationMode() == ScimConfig.AuthenticationMode.KEYCLOAK) {
            ClientConnection clientConnection = context.getConnection();

            AuthenticationManager.AuthResult auth = new AppAuthManager.BearerTokenAuthenticator(session)
                    .setRealm(realm)
                    .setConnection(clientConnection)
                    .setHeaders(headers)
                    .authenticate();

            if (auth == null || auth.getUser() == null || auth.getToken() == null) {
                logger.warn("Keycloak authentication failed");
                throw new NotAuthorizedException("Keycloak authentication failed");
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
        } else {
            ExternalTokenVerifier verifier = new ExternalTokenVerifier(
                    config.getExternalIssuer(),
                    config.getExternalJwksUri(),
                    config.getExternalAudience()
            );

            try {
                if (!verifier.verify(tokenString)) {
                    logger.warn("External token verification failed");
                    throw new NotAuthorizedException("External token verification failed");
                }
            } catch (URISyntaxException | IOException | InterruptedException | JWSInputException e) {
                logger.warn("Failed to verify permissions", e);
                throw new NotAuthorizedException(e);
            }
        }
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

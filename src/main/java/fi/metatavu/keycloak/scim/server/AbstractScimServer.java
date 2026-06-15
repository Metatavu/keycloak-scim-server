package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.authentication.Verifier;
import fi.metatavu.keycloak.scim.server.authentication.VerifierFactory;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.groups.GroupsController;
import fi.metatavu.keycloak.scim.server.metadata.MetadataController;
import fi.metatavu.keycloak.scim.server.users.UsersController;
import java.util.Base64;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.common.ClientConnection;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

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
    protected AbstractScimServer() {
        metadataController = new MetadataController();
        usersController = new UsersController();
        groupsController = new GroupsController();
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

        if (authorization == null) {
            logger.warn("Missing Authorization header");
            throw new NotAuthorizedException("Missing Authorization header");
        }

        if (config.getAuthenticationMode() == ScimConfig.AuthenticationMode.KEYCLOAK) {
            keycloakAuthentication(context, session, realm, headers);
        } else if (authorization.startsWith("Basic ")) {
            basicAuthentication(config, authorization, session);
        } else {
            externalAuthentication(config, extractBearerToken(authorization), session);
        }
    }

    private void basicAuthentication(ScimConfig config, String authorization, KeycloakSession session) {
        String basicAuthUsername = config.getBasicAuthUsername();
        String basicAuthPassword = config.getBasicAuthPassword();

        if (basicAuthUsername == null || basicAuthUsername.isBlank() || basicAuthPassword == null || basicAuthPassword.isBlank()) {
            logger.warn("Basic auth credentials received but Basic auth is not configured");
            throw new NotAuthorizedException("Basic auth is not configured");
        }

        String encoded = authorization.substring("Basic ".length()).trim();
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid Base64 in Basic auth header");
            throw new NotAuthorizedException("Invalid Basic auth header");
        }

        Verifier verifier = VerifierFactory.buildBasicAuth(config, session);

        if (!verifier.verify(decoded)) {
            logger.warn("Basic auth verification failed");
            throw new NotAuthorizedException("Basic auth verification failed");
        }
    }

    private void externalAuthentication(ScimConfig config, String tokenString, KeycloakSession session) {
        Verifier verifier = VerifierFactory.build(config, session);

        if (!verifier.verify(tokenString)) {
            logger.warn("External token verification failed");
            throw new NotAuthorizedException("External token verification failed");
        }
    }

    private void keycloakAuthentication(KeycloakContext context, KeycloakSession session, RealmModel realm, HttpHeaders headers) {
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
    }

    private String extractBearerToken(String authorization) {
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        } else {
            logger.warn("Invalid Authorization header");
            throw new NotAuthorizedException("Invalid Authorization header");
        }
    }

    /**
     * Checks if the given email is valid
     *
     * @param email email to check
     * @return true if the email is valid; false otherwise
     */
    protected boolean isValidEmail(String email) {
        try {
            new InternetAddress(email).validate();
        } catch (AddressException e) {
            return false;
        }

        return true;
    }

    /**
     * Checks if the given string is not null and not blank
     *
     * @param str string to check
     * @return true if the string is not null and not blank; false otherwise
     */
    protected boolean isBlank(String str) {
        return str == null || str.isBlank();
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

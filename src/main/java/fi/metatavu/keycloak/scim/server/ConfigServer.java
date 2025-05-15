package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.model.RealmScimConfigUpdateRequest;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.common.ClientConnection;
import org.keycloak.models.*;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;



public class ConfigServer {
    private static final Logger logger = Logger.getLogger(ConfigServer.class.getName());

    /**
     * Constructor
     */
    public ConfigServer() {
    }

    public Response updateRealmAuthConfig(RealmModel realm, fi.metatavu.keycloak.scim.server.model.RealmScimConfigUpdateRequest createRequest) {
        realm.setAttribute("scim.authentication.mode", createRequest.getAuthenticationMode().toString());

        if (createRequest.getAuthenticationMode() == RealmScimConfigUpdateRequest.AuthenticationModeEnum.EXTERNAL){
            if(createRequest.getExternalIssuer() == null){
                return Response.status(Response.Status.BAD_REQUEST).entity("External issuer is mandatory").build();
            }
            realm.setAttribute("scim.external.issuer", createRequest.getExternalIssuer().toString());

            if(createRequest.getJwksUri() == null){
                return Response.status(Response.Status.BAD_REQUEST).entity("JWKS uri is mandatory").build();
            }
            realm.setAttribute("scim.external.jwks.uri", createRequest.getJwksUri().toString());

            if(createRequest.getAudience() != null){
                realm.setAttribute("scim.external.audience", createRequest.getAudience());
            }

        }else{
            realm.removeAttribute("scim.external.issuer");
            realm.removeAttribute("scim.external.jwks.uri");
            realm.removeAttribute("scim.external.audience");
        }

        return Response.noContent().build();
    }


    public void verifyPermissions(KeycloakSession session) {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            throw new NotFoundException("Realm not found");
        }

        KeycloakContext context = session.getContext();

        if (context == null) {
            logger.warn("Keycloak context not found");
            throw new InternalServerErrorException("Keycloak context not found");
        }

        HttpHeaders headers = context.getRequestHeaders();
        String authorization = headers.getHeaderString(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            logger.warn("Missing or invalid Authorization header");
            throw new NotAuthorizedException("Missing or invalid Authorization header");
        }

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

        RoleModel roleModel = realm.getRole(ScimRoles.SERVICE_ACCOUNT_CONFIG_ADMIN_ROLE);
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

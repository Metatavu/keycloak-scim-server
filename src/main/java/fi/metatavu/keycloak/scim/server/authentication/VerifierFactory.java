package fi.metatavu.keycloak.scim.server.authentication;

import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import fi.metatavu.keycloak.scim.server.config.ScimConfig.AuthenticationMode;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

/**
 * Verifier factory
 */
public class VerifierFactory {

    private static final Logger logger = Logger.getLogger(VerifierFactory.class.getName());

    private VerifierFactory() {}

    /**
     * Builds a verifier for Bearer token authentication based on the ScimConfig
     */
    public static Verifier build(ScimConfig config, KeycloakSession session) {
        if (config.getAuthenticationMode() != AuthenticationMode.EXTERNAL) {
            throw new IllegalArgumentException("Authentication mode must be EXTERNAL");
        }
        String sharedSecret = config.getSharedSecret();
        if (sharedSecret == null || sharedSecret.isBlank()) {
            return new ExternalTokenVerifier(
                config.getExternalIssuer(),
                config.getExternalJwksUri(),
                config.getExternalAudience());
        } else {
            return new ExternalSharedSecretVerifier(session, sharedSecret);
        }
    }

    /**
     * Builds a verifier for Basic Auth authentication based on the ScimConfig
     */
    public static Verifier buildBasicAuth(ScimConfig config, KeycloakSession session) {
        if (config.getAuthenticationMode() != AuthenticationMode.EXTERNAL) {
            throw new IllegalArgumentException("Authentication mode must be EXTERNAL");
        }
        return new BasicAuthVerifier(session, config.getBasicAuthUsername(), config.getBasicAuthPassword());
    }
}

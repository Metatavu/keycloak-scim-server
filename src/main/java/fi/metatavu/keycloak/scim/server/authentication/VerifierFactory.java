package fi.metatavu.keycloak.scim.server.authentication;

import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import fi.metatavu.keycloak.scim.server.config.ScimConfig.AuthenticationMode;
import org.keycloak.models.KeycloakSession;

/**
 * Verifier factory
 */
public class VerifierFactory {

    private VerifierFactory() {}

    /**
     * Builds the verifier based on the ScimConfig
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
}

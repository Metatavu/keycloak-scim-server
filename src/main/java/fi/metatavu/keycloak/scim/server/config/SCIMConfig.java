package fi.metatavu.keycloak.scim.server.config;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * SCIM Configuration reader
 */
public class SCIMConfig {

    private final Config config;

    public enum AuthenticationMode {
        KEYCLOAK, EXTERNAL
    }

    public SCIMConfig() {
        this.config = ConfigProvider.getConfig();
    }

    /**
     * Get SCIM Authentication mode
     */
    public AuthenticationMode getAuthenticationMode() {
        return config.getOptionalValue("scim.authentication.mode", String.class)
                .map(String::toUpperCase)
                .map(AuthenticationMode::valueOf)
                .orElse(AuthenticationMode.KEYCLOAK);
    }

    /**
     * Get External Token Issuer (if in EXTERNAL mode)
     */
    public String getExternalIssuer() {
        return config.getOptionalValue("scim.external.issuer", String.class).orElse(null);
    }

    /**
     * Get External Token JWKS URI (if in EXTERNAL mode)
     */
    public String getExternalJwksUri() {
        return config.getOptionalValue("scim.external.jwks.uri", String.class).orElse(null);
    }

    /**
     * Get External Token Audience (optional)
     */
    public String getExternalAudience() {
        return config.getOptionalValue("scim.external.audience", String.class).orElse(null);
    }

}
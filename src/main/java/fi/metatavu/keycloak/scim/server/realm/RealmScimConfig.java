package fi.metatavu.keycloak.scim.server.realm;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * SCIM configuration for realms
 */
public class RealmScimConfig implements ScimConfig {

    private final Config config;

    public RealmScimConfig() {
        this.config = ConfigProvider.getConfig();
    }

    /**
     * Validate configuration
     */
    @Override
    public void validateConfig() throws ConfigurationError {
        if (getAuthenticationMode() == null) {
            throw new ConfigurationError("SCIM_AUTHENTICATION_MODE is not set");
        }

        if (getAuthenticationMode() == AuthenticationMode.EXTERNAL) {
            if (getExternalIssuer() == null) {
                throw new ConfigurationError("SCIM_EXTERNAL_ISSUER is not set");
            }

            if (getExternalJwksUri() == null) {
                throw new ConfigurationError("SCIM_EXTERNAL_JWKS_URI is not set");
            }

            if (getExternalAudience() == null) {
                throw new ConfigurationError("SCIM_EXTERNAL_AUDIENCE is not set");
            }
        }
    }

    /**
     * Get SCIM Authentication mode
     */
    @Override
    public AuthenticationMode getAuthenticationMode() {
        return config.getOptionalValue("scim.authentication.mode", String.class)
                .map(String::toUpperCase)
                .map(AuthenticationMode::valueOf)
                .orElse(null);
    }

    /**
     * Get External Token Issuer (if in EXTERNAL mode)
     */
    @Override
    public String getExternalIssuer() {
        return config.getOptionalValue("scim.external.issuer", String.class).orElse(null);
    }

    /**
     * Get External Token JWKS URI (if in EXTERNAL mode)
     */
    @Override
    public String getExternalJwksUri() {
        return config.getOptionalValue("scim.external.jwks.uri", String.class).orElse(null);
    }

    /**
     * Get External Token Audience (optional)
     */
    @Override
    public String getExternalAudience() {
        return config.getOptionalValue("scim.external.audience", String.class).orElse(null);
    }

}

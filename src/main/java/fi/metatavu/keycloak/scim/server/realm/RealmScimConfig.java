package fi.metatavu.keycloak.scim.server.realm;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.keycloak.models.RealmModel;

import java.util.List;
import java.util.Optional;

/**
 * SCIM configuration for a Keycloak realm.
 */
public class RealmScimConfig implements ScimConfig {

    private final Config config;
    private final RealmModel realm;

    public RealmScimConfig(RealmModel realm) {
        this.config = ConfigProvider.getConfig();
        this.realm = realm;
    }

    /**
     * Validates the SCIM configuration for the realm.
     *
     * @throws ConfigurationError if any required configuration is missing
     */
    @Override
    public void validateConfig() throws ConfigurationError {
        AuthenticationMode mode = getAuthenticationMode();
        if (mode == null) {
            throw new ConfigurationError("SCIM_AUTHENTICATION_MODE is not set");
        }

        if (mode == AuthenticationMode.EXTERNAL) {
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
     * Returns the configured authentication mode.
     */
    @Override
    public AuthenticationMode getAuthenticationMode() {
        return readRealmAttribute("scim.authentication.mode")
                .map(String::toUpperCase)
                .map(AuthenticationMode::valueOf)
                .or(() -> config.getOptionalValue("scim.authentication.mode", String.class)
                        .map(String::toUpperCase)
                        .map(AuthenticationMode::valueOf))
                .orElse(null);
    }

    /**
     * Returns the external token issuer (if using EXTERNAL mode).
     */
    @Override
    public String getExternalIssuer() {
        return readRealmAttribute("scim.external.issuer")
                .or(() -> config.getOptionalValue("scim.external.issuer", String.class))
                .orElse(null);
    }

    /**
     * Returns the external JWKS URI (if using EXTERNAL mode).
     */
    @Override
    public String getExternalJwksUri() {
        return readRealmAttribute("scim.external.jwks.uri")
                .or(() -> config.getOptionalValue("scim.external.jwks.uri", String.class))
                .orElse(null);
    }

    /**
     * Returns the external audience (if using EXTERNAL mode).
     */
    @Override
    public String getExternalAudience() {
        return readRealmAttribute("scim.external.audience")
                .or(() -> config.getOptionalValue("scim.external.audience", String.class))
                .orElse(null);
    }

    /**
     * Returns whether IDP linking is enabled.
     */
    @Override
    public boolean getLinkIdp() {
        return false;
    }

    /**
     * Helper method to read the first string from a realm attribute.
     */
    private Optional<String> readRealmAttribute(String key) {
        String value = realm.getAttribute(key);
        return Optional.ofNullable(value);
    }
}
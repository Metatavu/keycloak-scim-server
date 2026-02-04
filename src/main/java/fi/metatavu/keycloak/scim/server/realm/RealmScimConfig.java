package fi.metatavu.keycloak.scim.server.realm;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.keycloak.models.RealmModel;

import java.util.Optional;

/**
 * SCIM configuration for a Keycloak realm.
 */
public class RealmScimConfig implements ScimConfig {

    public static final String SCIM_EXTERNAL_JWKS_URI = "scim.external.jwks.uri";
    public static final String SCIM_EXTERNAL_AUDIENCE = "scim.external.audience";
    public static final String SCIM_EXTERNAL_SHARED_SECRET = "scim.external.shared.secret";
    public static final String SCIM_AUTHENTICATION_MODE = "scim.authentication.mode";
    public static final String SCIM_EXTERNAL_ISSUER = "scim.external.issuer";
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

        boolean isSharedSecretPresent = getSharedSecret() != null && !getSharedSecret().isBlank();

        if (mode == AuthenticationMode.EXTERNAL && !isSharedSecretPresent) {
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
        return readRealmAttribute(SCIM_AUTHENTICATION_MODE)
                .map(String::toUpperCase)
                .map(AuthenticationMode::valueOf)
                .or(() -> config.getOptionalValue(SCIM_AUTHENTICATION_MODE, String.class)
                        .map(String::toUpperCase)
                        .map(AuthenticationMode::valueOf))
                .orElse(null);
    }

    /**
     * Returns the external token issuer (if using EXTERNAL mode).
     */
    @Override
    public String getExternalIssuer() {
        return readRealmAttribute(SCIM_EXTERNAL_ISSUER)
                .or(() -> config.getOptionalValue(SCIM_EXTERNAL_ISSUER, String.class))
                .orElse(null);
    }

    /**
     * Returns the external JWKS URI (if using EXTERNAL mode).
     */
    @Override
    public String getExternalJwksUri() {
        return readRealmAttribute(SCIM_EXTERNAL_JWKS_URI)
                .or(() -> config.getOptionalValue(SCIM_EXTERNAL_JWKS_URI, String.class))
                .orElse(null);
    }

    /**
     * Returns the external audience (if using EXTERNAL mode).
     */
    @Override
    public String getExternalAudience() {
        return readRealmAttribute(SCIM_EXTERNAL_AUDIENCE)
                .or(() -> config.getOptionalValue(SCIM_EXTERNAL_AUDIENCE, String.class))
                .orElse(null);
    }

    @Override
    public String getSharedSecret() {
        return readRealmAttribute(SCIM_EXTERNAL_SHARED_SECRET)
            .or(() -> config.getOptionalValue(SCIM_EXTERNAL_SHARED_SECRET, String.class))
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
     * Returns whether email should be used as username.
     */
    @Override
    public boolean getEmailAsUsername() {
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

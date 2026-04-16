package fi.metatavu.keycloak.scim.server.realm;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.keycloak.models.RealmModel;

import java.util.Optional;

/**
 * SCIM configuration for a Keycloak realm.
 */
public class RealmScimConfig implements ScimConfig {

    private static final Logger logger = Logger.getLogger(RealmScimConfig.class.getName());

    public static final String SCIM_EXTERNAL_JWKS_URI = "scim.external.jwks.uri";
    public static final String SCIM_EXTERNAL_AUDIENCE = "scim.external.audience";
    public static final String SCIM_EXTERNAL_SHARED_SECRET = "scim.external.shared.secret";
    public static final String SCIM_AUTHENTICATION_MODE = "scim.authentication.mode";
    public static final String SCIM_EXTERNAL_ISSUER = "scim.external.issuer";
    public static final String SCIM_LINK_IDP = "scim.link.idp";
    public static final String SCIM_IDENTITY_PROVIDER_ALIAS = "scim.identity.provider.alias";
    public static final String SCIM_EMAIL_AS_USERNAME = "scim.email.as.username";
    public static final String SCIM_BASIC_AUTH_USERNAME = "scim.basic.auth.username";
    public static final String SCIM_BASIC_AUTH_PASSWORD = "scim.basic.auth.password";
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
            logger.warn("Realm SCIM config invalid: SCIM_AUTHENTICATION_MODE is not set");
            throw new ConfigurationError("SCIM_AUTHENTICATION_MODE is not set");
        }

        logger.debugf("Realm SCIM authentication mode: %s", mode);

        boolean isSharedSecretPresent = getSharedSecret() != null && !getSharedSecret().isBlank();
        boolean isBasicAuthUsernamePresent = getBasicAuthUsername() != null && !getBasicAuthUsername().isBlank();
        boolean isBasicAuthPasswordPresent = getBasicAuthPassword() != null && !getBasicAuthPassword().isBlank();

        if (mode == AuthenticationMode.EXTERNAL) {
            if (isBasicAuthUsernamePresent || isBasicAuthPasswordPresent) {
                if (!isBasicAuthUsernamePresent) {
                    logger.warn("Realm SCIM config invalid: SCIM_BASIC_AUTH_USERNAME is not set");
                    throw new ConfigurationError("SCIM_BASIC_AUTH_USERNAME must be set when SCIM_BASIC_AUTH_PASSWORD is set");
                }
                if (!isBasicAuthPasswordPresent) {
                    logger.warn("Realm SCIM config invalid: SCIM_BASIC_AUTH_PASSWORD is not set");
                    throw new ConfigurationError("SCIM_BASIC_AUTH_PASSWORD must be set when SCIM_BASIC_AUTH_USERNAME is set");
                }
            } else if (!isSharedSecretPresent) {
                if (getExternalIssuer() == null) {
                    logger.warn("Realm SCIM config invalid: SCIM_EXTERNAL_ISSUER is not set");
                    throw new ConfigurationError("SCIM_EXTERNAL_ISSUER is not set");
                }

                if (getExternalJwksUri() == null) {
                    logger.warn("Realm SCIM config invalid: SCIM_EXTERNAL_JWKS_URI is not set");
                    throw new ConfigurationError("SCIM_EXTERNAL_JWKS_URI is not set");
                }

                if (getExternalAudience() == null) {
                    logger.warn("Realm SCIM config invalid: SCIM_EXTERNAL_AUDIENCE is not set");
                    throw new ConfigurationError("SCIM_EXTERNAL_AUDIENCE is not set");
                }
            }
        }

        if (getLinkIdp() && getIdentityProviderAlias() == null) {
            throw new ConfigurationError("SCIM_IDENTITY_PROVIDER_ALIAS must be set when SCIM_LINK_IDP is true");
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
        return readRealmAttribute(SCIM_LINK_IDP)
            .map(Boolean::parseBoolean)
            .or(() -> config.getOptionalValue(SCIM_LINK_IDP, Boolean.class))
            .orElse(false);
    }

    /**
     * Returns the configured identity provider alias for user linking.
     */
    @Override
    public String getIdentityProviderAlias() {
        return readRealmAttribute(SCIM_IDENTITY_PROVIDER_ALIAS)
            .or(() -> config.getOptionalValue(SCIM_IDENTITY_PROVIDER_ALIAS, String.class))
            .orElse(null);
    }

    /**
     * Returns whether email should be used as username.
     */
    @Override
    public boolean getEmailAsUsername() {
        return readRealmAttribute(SCIM_EMAIL_AS_USERNAME)
            .map(Boolean::parseBoolean)
            .or(() -> config.getOptionalValue(SCIM_EMAIL_AS_USERNAME, Boolean.class))
            .orElse(false);
    }

    @Override
    public String getBasicAuthUsername() {
        return readRealmAttribute(SCIM_BASIC_AUTH_USERNAME)
                .or(() -> config.getOptionalValue(SCIM_BASIC_AUTH_USERNAME, String.class))
                .orElse(null);
    }

    @Override
    public String getBasicAuthPassword() {
        return readRealmAttribute(SCIM_BASIC_AUTH_PASSWORD)
                .or(() -> config.getOptionalValue(SCIM_BASIC_AUTH_PASSWORD, String.class))
                .orElse(null);
    }

    /**
     * Helper method to read the first string from a realm attribute.
     */
    private Optional<String> readRealmAttribute(String key) {
        String value = realm.getAttribute(key);
        return Optional.ofNullable(value);
    }
}

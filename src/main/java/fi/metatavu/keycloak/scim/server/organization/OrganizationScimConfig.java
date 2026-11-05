package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import org.jboss.logging.Logger;

/**
 * SCIM configuration for organizations
 */
public interface OrganizationScimConfig extends ScimConfig {

    Logger logger = Logger.getLogger(OrganizationScimConfig.class.getName());

    public static final String SCIM_EXTERNAL_SHARED_SECRET = "SCIM_EXTERNAL_SHARED_SECRET";
    public static final String SCIM_EXTERNAL_JWKS_URI = "SCIM_EXTERNAL_JWKS_URI";
    public static final String SCIM_EXTERNAL_AUDIENCE = "SCIM_EXTERNAL_AUDIENCE";
    public static final String SCIM_LINK_IDP = "SCIM_LINK_IDP";
    public static final String SCIM_EXTERNAL_ISSUER = "SCIM_EXTERNAL_ISSUER";
    public static final String SCIM_AUTHENTICATION_MODE = "SCIM_AUTHENTICATION_MODE";
    public static final String SCIM_EMAIL_AS_USERNAME = "SCIM_EMAIL_AS_USERNAME";
    public static final String SCIM_DEFAULT_GROUPS = "SCIM_DEFAULT_GROUPS";
    public static final String SCIM_BASIC_AUTH_USERNAME = "SCIM_BASIC_AUTH_USERNAME";
    public static final String SCIM_BASIC_AUTH_PASSWORD = "SCIM_BASIC_AUTH_PASSWORD";

    default void validateConfig() throws ConfigurationError {
        AuthenticationMode mode = getAuthenticationMode();
        if (mode == null) {
            logger.warnf("Organization SCIM config invalid: %s is not set", SCIM_AUTHENTICATION_MODE);
            throw new ConfigurationError(SCIM_AUTHENTICATION_MODE + " is not set");
        }

        logger.debugf("Organization SCIM authentication mode: %s", mode);

        boolean isSharedSecretPresent = getSharedSecret() != null && !getSharedSecret().isBlank();
        boolean isBasicAuthUsernamePresent = getBasicAuthUsername() != null && !getBasicAuthUsername().isBlank();
        boolean isBasicAuthPasswordPresent = getBasicAuthPassword() != null && !getBasicAuthPassword().isBlank();

        if (mode == AuthenticationMode.EXTERNAL) {
            if (isBasicAuthUsernamePresent || isBasicAuthPasswordPresent) {
                if (!isBasicAuthUsernamePresent) {
                    logger.warnf("Organization SCIM config invalid: %s is not set", SCIM_BASIC_AUTH_USERNAME);
                    throw new ConfigurationError(SCIM_BASIC_AUTH_USERNAME + " must be set when " + SCIM_BASIC_AUTH_PASSWORD + " is set");
                }
                if (!isBasicAuthPasswordPresent) {
                    logger.warnf("Organization SCIM config invalid: %s is not set", SCIM_BASIC_AUTH_PASSWORD);
                    throw new ConfigurationError(SCIM_BASIC_AUTH_PASSWORD + " must be set when " + SCIM_BASIC_AUTH_USERNAME + " is set");
                }
            } else if (!isSharedSecretPresent) {
                if (getExternalIssuer() == null) {
                    logger.warnf("Organization SCIM config invalid: %s is not set", SCIM_EXTERNAL_ISSUER);
                    throw new ConfigurationError(SCIM_EXTERNAL_ISSUER + " is not set");
                }

                if (getExternalJwksUri() == null) {
                    logger.warnf("Organization SCIM config invalid: %s is not set", SCIM_EXTERNAL_JWKS_URI);
                    throw new ConfigurationError(SCIM_EXTERNAL_JWKS_URI + " is not set");
                }

                if (getExternalAudience() == null) {
                    logger.warnf("Organization SCIM config invalid: %s is not set", SCIM_EXTERNAL_AUDIENCE);
                    throw new ConfigurationError(SCIM_EXTERNAL_AUDIENCE + " is not set");
                }
            }
        } else {
            logger.warnf("Organization SCIM config invalid: authentication mode %s is not supported in organization mode", mode);
            throw new ConfigurationError(
                String.format(
                    SCIM_AUTHENTICATION_MODE + " %s AuthenticationMode not supported in organization mode",
                    mode
                )
            );
        }
    }

    // Organization SCIM configuration does not support identity provider alias, so we return empty string
    @Override
    default String getIdentityProviderAlias() {
        return "";
    }

    /**
     * Is the organization enabled for SCIM
     */
    public boolean isEnabled();

}

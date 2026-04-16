package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import org.keycloak.models.OrganizationModel;

/**
 * SCIM configuration for organizations
 */
public class OrganizationScimConfig implements ScimConfig {

    private static final Logger logger = Logger.getLogger(OrganizationScimConfig.class.getName());

    public static final String SCIM_EXTERNAL_SHARED_SECRET = "SCIM_EXTERNAL_SHARED_SECRET";
    public static final String SCIM_EXTERNAL_JWKS_URI = "SCIM_EXTERNAL_JWKS_URI";
    public static final String SCIM_EXTERNAL_AUDIENCE = "SCIM_EXTERNAL_AUDIENCE";
    public static final String SCIM_LINK_IDP = "SCIM_LINK_IDP";
    public static final String SCIM_EXTERNAL_ISSUER = "SCIM_EXTERNAL_ISSUER";
    public static final String SCIM_AUTHENTICATION_MODE = "SCIM_AUTHENTICATION_MODE";
    public static final String SCIM_EMAIL_AS_USERNAME = "SCIM_EMAIL_AS_USERNAME";
    public static final String SCIM_BASIC_AUTH_USERNAME = "SCIM_BASIC_AUTH_USERNAME";
    public static final String SCIM_BASIC_AUTH_PASSWORD = "SCIM_BASIC_AUTH_PASSWORD";

    private final OrganizationModel organization;

    public OrganizationScimConfig(OrganizationModel organization) {
        this.organization = organization;
    }

    @Override
    public void validateConfig() throws ConfigurationError {
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

    @Override
    public AuthenticationMode getAuthenticationMode() {
        String value = getAttribute(SCIM_AUTHENTICATION_MODE);
        if (value == null || value.isEmpty()) {
            return null;
        }

        return AuthenticationMode.valueOf(value);
    }

    @Override
    public String getExternalIssuer() {
        return getAttribute(SCIM_EXTERNAL_ISSUER);
    }

    @Override
    public String getExternalJwksUri() {
        return getAttribute(SCIM_EXTERNAL_JWKS_URI);
    }

    @Override
    public String getExternalAudience() {
        return getAttribute(SCIM_EXTERNAL_AUDIENCE);
    }

    @Override
    public String getSharedSecret() {
        return getAttribute(SCIM_EXTERNAL_SHARED_SECRET);
    }

    @Override
    public boolean getLinkIdp() {
        return "true".equalsIgnoreCase(getAttribute(SCIM_LINK_IDP));
    }

    // Organization SCIM configuration does not support identity provider alias, so we return empty string
    @Override
    public String getIdentityProviderAlias() {
        return "";
    }

    @Override
    public boolean getEmailAsUsername() {
        return "true".equalsIgnoreCase(getAttribute(SCIM_EMAIL_AS_USERNAME));
    }

    @Override
    public String getBasicAuthUsername() {
        return getAttribute(SCIM_BASIC_AUTH_USERNAME);
    }

    @Override
    public String getBasicAuthPassword() {
        return getAttribute(SCIM_BASIC_AUTH_PASSWORD);
    }

    /**
     * Gets the organization attribute
     *
     * @return organization attribute value
     */
    private String getAttribute(String attributeName) {
        Map<String, List<String>> attributes = organization.getAttributes();
        if (attributes == null) {
            return null;
        }

        List<String> values = attributes.get(attributeName);
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.getFirst();
    }


}

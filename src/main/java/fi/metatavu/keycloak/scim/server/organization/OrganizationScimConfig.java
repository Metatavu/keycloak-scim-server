package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import java.util.List;
import java.util.Map;

/**
 * SCIM configuration for organizations
 */
public interface OrganizationScimConfig extends ScimConfig {

    public static final String SCIM_EXTERNAL_SHARED_SECRET = "SCIM_EXTERNAL_SHARED_SECRET";
    public static final String SCIM_EXTERNAL_JWKS_URI = "SCIM_EXTERNAL_JWKS_URI";
    public static final String SCIM_EXTERNAL_AUDIENCE = "SCIM_EXTERNAL_AUDIENCE";
    public static final String SCIM_LINK_IDP = "SCIM_LINK_IDP";
    public static final String SCIM_EXTERNAL_ISSUER = "SCIM_EXTERNAL_ISSUER";
    public static final String SCIM_AUTHENTICATION_MODE = "SCIM_AUTHENTICATION_MODE";
    public static final String SCIM_EMAIL_AS_USERNAME = "SCIM_EMAIL_AS_USERNAME";

    default void validateConfig() throws ConfigurationError {
        AuthenticationMode mode = getAuthenticationMode();
        if (mode == null) {
            throw new ConfigurationError(SCIM_AUTHENTICATION_MODE + " is not set");
        }

        boolean isSharedSecretPresent = getSharedSecret() != null && !getSharedSecret().isBlank();

        if (mode == AuthenticationMode.EXTERNAL) {
            if (!isSharedSecretPresent) {
                if (getExternalIssuer() == null) {
                    throw new ConfigurationError(SCIM_EXTERNAL_ISSUER + " is not set");
                }

                if (getExternalJwksUri() == null) {
                    throw new ConfigurationError(SCIM_EXTERNAL_JWKS_URI + " is not set");
                }

                if (getExternalAudience() == null) {
                    throw new ConfigurationError(SCIM_EXTERNAL_AUDIENCE + " is not set");
                }
            }
        } else {
            throw new ConfigurationError(
                String.format(
                    SCIM_AUTHENTICATION_MODE + " %s AuthenticationMode not supported in organization mode",
                    mode
                )
            );
        }
    }

    /**
     * Is the organization enabled for SCIM
     */
    public boolean isEnabled();

}

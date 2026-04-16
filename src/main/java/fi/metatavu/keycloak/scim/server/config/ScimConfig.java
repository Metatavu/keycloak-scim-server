package fi.metatavu.keycloak.scim.server.config;

/**
 * SCIM Configuration
 */
public interface ScimConfig {

    /**
     * SCIM Authentication modes
     */
    enum AuthenticationMode {
        KEYCLOAK,
        EXTERNAL
    }

    /**
     * Validates the configuration
     *
     * @throws ConfigurationError thrown if the configuration is invalid
     */
    void validateConfig() throws ConfigurationError;

    /**
     * Gets the SCIM Authentication mode
     *
     * @return authentication mode
     */
    AuthenticationMode getAuthenticationMode();

    /**
     * Gets the external token issuer (if in EXTERNAL mode)
     *
     * @return external token issuer
     */
    String getExternalIssuer();

    /**
     * Gets the external token JWKS URI (if in EXTERNAL mode)
     *
     * @return external token JWKS URI
     */
    String getExternalJwksUri();

    /**
     * Gets the external audience (if in EXTERNAL mode)
     *
     * @return external audience
     */
    String getExternalAudience();

    /**
     * Gets the shared secret (if in EXTERNAL mode) using PHC String format
     *
     * @return shared secret
     */
    String getSharedSecret();

    /**
     * Returns whether identity provider should be automatically linked
     *
     * @return true if identity provider should be automatically linked
     */
    boolean getLinkIdp();

    /**
     * Returns the identity provider alias to link users to
     *
     * @return identity provider alias or null if not configured
     */
    String getIdentityProviderAlias();

    /**
     * Returns whether email should be used as username instead of username
     *
     * @return true if email should be used as username
     */
    boolean getEmailAsUsername();

    /**
     * Gets the basic auth username (if using EXTERNAL mode with Basic auth)
     *
     * @return basic auth username or null if not configured
     */
    String getBasicAuthUsername();

    /**
     * Gets the basic auth password in PHC String format (if using EXTERNAL mode with Basic auth)
     *
     * @return basic auth password hash or null if not configured
     */
    String getBasicAuthPassword();
}

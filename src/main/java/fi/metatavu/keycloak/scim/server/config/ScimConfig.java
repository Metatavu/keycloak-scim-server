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
     * Returns whether identity provider should be automatically linked
     *
     * @return true if identity provider should be automatically linked
     */
    boolean getLinkIdp();

    /**
     * Returns whether email should be used as username instead of username
     *
     * @return true if email should be used as username
     */
    boolean getEmailAsUsername();
}
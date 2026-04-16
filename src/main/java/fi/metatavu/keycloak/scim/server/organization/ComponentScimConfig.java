package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

import static org.keycloak.provider.ProviderConfigProperty.*;

/**
 * SCIM configuration backed by a Keycloak ComponentModel.
 * Used when SCIM config is managed through the User Federation GUI.
 */
public class ComponentScimConfig implements ScimConfig {

    private static final Logger logger = Logger.getLogger(ComponentScimConfig.class.getName());

    public static final String ENABLED_PROPERTY = "ENABLED";
    public static final String ORGANIZATION_ID = "ORGANIZATION_ID";

    private static final List<ProviderConfigProperty> PROPERTIES = List.of(
        new ProviderConfigProperty(
            ORGANIZATION_ID,
            "Organization ID",
            "Organization ID this SCIM config belongs to. The component ID will be set to match.",
            STRING_TYPE,
            null
        ),
        new ProviderConfigProperty(
            OrganizationScimConfig.SCIM_AUTHENTICATION_MODE,
            "Authentication Mode",
            "Authentication mode for SCIM API. Possible values are KEYCLOAK and EXTERNAL.",
            LIST_TYPE,
            "EXTERNAL",
            "KEYCLOAK",
            "EXTERNAL"
        ),
        new ProviderConfigProperty(
            OrganizationScimConfig.SCIM_EXTERNAL_ISSUER,
            "External Issuer",
            "Issuer for the external authentication. This is used to validate the JWT token.",
            STRING_TYPE,
            null
        ),
        new ProviderConfigProperty(
            OrganizationScimConfig.SCIM_EXTERNAL_AUDIENCE,
            "External Audience",
            "Audience for the external authentication. This is used to validate the JWT token.",
            STRING_TYPE,
            null
        ),
        new ProviderConfigProperty(
            OrganizationScimConfig.SCIM_EXTERNAL_JWKS_URI,
            "External JWKS URI",
            "JWKS URI for the external authentication. This is used to validate the JWT token.",
            STRING_TYPE,
            null
        ),
        new ProviderConfigProperty(
            OrganizationScimConfig.SCIM_EXTERNAL_SHARED_SECRET,
            "External Shared Secret",
            "Shared secret value used for request authentication/validation.",
            STRING_TYPE,
            null
        ),
        new ProviderConfigProperty(
            OrganizationScimConfig.SCIM_LINK_IDP,
            "Link to Organization IdP",
            "Enables support for linking organization identity provider with user.",
            BOOLEAN_TYPE,
            "false"
        ),
        new ProviderConfigProperty(
            OrganizationScimConfig.SCIM_EMAIL_AS_USERNAME,
            "Use Email as Username",
            "Forces server to use email as username instead of actual username.",
            BOOLEAN_TYPE,
            "false"
        ),
        new ProviderConfigProperty(
            OrganizationScimConfig.SCIM_BASIC_AUTH_USERNAME,
            "Basic Auth Username",
            "Username for HTTP Basic authentication.",
            STRING_TYPE,
            null
        ),
        new ProviderConfigProperty(
            OrganizationScimConfig.SCIM_BASIC_AUTH_PASSWORD,
            "Basic Auth Password",
            "Password hash in PHC String Format for HTTP Basic authentication.",
            STRING_TYPE,
            null
        )
    );

    private final ComponentModel model;

    public ComponentScimConfig(ComponentModel model) {
        this.model = model;
    }

    /**
     * Returns the config properties for the GUI
     *
     * @return config properties
     */
    public static List<ProviderConfigProperty> getConfigProperties() {
        return PROPERTIES;
    }

    /**
     * Returns the component ID
     *
     * @return component ID
     */
    public String getId() {
        return model.getId();
    }

    /**
     * Sets the component ID
     *
     * @param id component ID
     */
    public void setId(String id) {
        model.setId(id);
    }

    /**
     * Returns the organization ID
     *
     * @return organization ID
     */
    public String getOrganizationId() {
        return model.get(ORGANIZATION_ID);
    }

    @Override
    public boolean isEnabled() {
        return model.get(ENABLED_PROPERTY, true);
    }

    @Override
    public void validateConfig() throws ConfigurationError {
        AuthenticationMode mode = getAuthenticationMode();
        if (mode == null) {
            throw new ConfigurationError(OrganizationScimConfig.SCIM_AUTHENTICATION_MODE + " is not set");
        }

        if (mode == AuthenticationMode.EXTERNAL) {
            boolean isSharedSecretPresent = getSharedSecret() != null && !getSharedSecret().isBlank();
            boolean isBasicAuthUsernamePresent = getBasicAuthUsername() != null && !getBasicAuthUsername().isBlank();
            boolean isBasicAuthPasswordPresent = getBasicAuthPassword() != null && !getBasicAuthPassword().isBlank();

            if (isBasicAuthUsernamePresent || isBasicAuthPasswordPresent) {
                if (!isBasicAuthUsernamePresent) {
                    throw new ConfigurationError(OrganizationScimConfig.SCIM_BASIC_AUTH_USERNAME + " must be set when " + OrganizationScimConfig.SCIM_BASIC_AUTH_PASSWORD + " is set");
                }
                if (!isBasicAuthPasswordPresent) {
                    throw new ConfigurationError(OrganizationScimConfig.SCIM_BASIC_AUTH_PASSWORD + " must be set when " + OrganizationScimConfig.SCIM_BASIC_AUTH_USERNAME + " is set");
                }
            } else if (!isSharedSecretPresent) {
                if (getExternalIssuer() == null) {
                    throw new ConfigurationError(OrganizationScimConfig.SCIM_EXTERNAL_ISSUER + " is not set");
                }
                if (getExternalJwksUri() == null) {
                    throw new ConfigurationError(OrganizationScimConfig.SCIM_EXTERNAL_JWKS_URI + " is not set");
                }
                if (getExternalAudience() == null) {
                    throw new ConfigurationError(OrganizationScimConfig.SCIM_EXTERNAL_AUDIENCE + " is not set");
                }
            }
        } else {
            throw new ConfigurationError(
                String.format(
                    OrganizationScimConfig.SCIM_AUTHENTICATION_MODE + " %s AuthenticationMode not supported in organization mode",
                    mode
                )
            );
        }
    }

    @Override
    public AuthenticationMode getAuthenticationMode() {
        String value = model.get(OrganizationScimConfig.SCIM_AUTHENTICATION_MODE);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return AuthenticationMode.valueOf(value);
    }

    @Override
    public String getExternalIssuer() {
        return model.get(OrganizationScimConfig.SCIM_EXTERNAL_ISSUER);
    }

    @Override
    public String getExternalJwksUri() {
        return model.get(OrganizationScimConfig.SCIM_EXTERNAL_JWKS_URI);
    }

    @Override
    public String getExternalAudience() {
        return model.get(OrganizationScimConfig.SCIM_EXTERNAL_AUDIENCE);
    }

    @Override
    public String getSharedSecret() {
        return model.get(OrganizationScimConfig.SCIM_EXTERNAL_SHARED_SECRET);
    }

    @Override
    public boolean getLinkIdp() {
        return model.get(OrganizationScimConfig.SCIM_LINK_IDP, false);
    }

    @Override
    public String getIdentityProviderAlias() {
        return "";
    }

    @Override
    public boolean getEmailAsUsername() {
        return model.get(OrganizationScimConfig.SCIM_EMAIL_AS_USERNAME, false);
    }

    @Override
    public String getBasicAuthUsername() {
        return model.get(OrganizationScimConfig.SCIM_BASIC_AUTH_USERNAME);
    }

    @Override
    public String getBasicAuthPassword() {
        return model.get(OrganizationScimConfig.SCIM_BASIC_AUTH_PASSWORD);
    }
}

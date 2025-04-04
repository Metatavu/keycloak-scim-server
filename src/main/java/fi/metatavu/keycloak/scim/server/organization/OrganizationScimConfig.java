package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import org.keycloak.models.OrganizationModel;

import java.util.List;
import java.util.Map;

/**
 * SCIM configuration for organizations
 */
public class OrganizationScimConfig implements ScimConfig {

    private final OrganizationModel organization;

    public OrganizationScimConfig(OrganizationModel organization) {
        this.organization = organization;
    }

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
        } else {
            throw new ConfigurationError(String.format("SCIM_AUTHENTICATION_MODE %s AuthenticationMode not supported in organization mode", getAuthenticationMode()));
        }
    }

    @Override
    public AuthenticationMode getAuthenticationMode() {
        String value = getAttribute("SCIM_AUTHENTICATION_MODE");
        if (value == null || value.isEmpty()) {
            return null;
        }

        return AuthenticationMode.valueOf(value);
    }

    @Override
    public String getExternalIssuer() {
        return getAttribute("SCIM_EXTERNAL_ISSUER");
    }

    @Override
    public String getExternalJwksUri() {
        return getAttribute("SCIM_EXTERNAL_JWKS_URI");
    }

    @Override
    public String getExternalAudience() {
        return getAttribute("SCIM_EXTERNAL_AUDIENCE");
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

package fi.metatavu.keycloak.scim.server.organization.keycloak;

import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.organization.OrganizationScimConfig;
import org.keycloak.models.OrganizationModel;
import java.util.List;
import java.util.Map;

/**
 * SCIM configuration for organizations
 */
public class KeycloakOrganizationScimConfig implements OrganizationScimConfig {

    private final OrganizationModel organization;
  
    public KeycloakOrganizationScimConfig(OrganizationModel organization) {
        this.organization = organization;  
    }

    @Override
    public boolean isEnabled() {
      try {
        validateConfig();
        return true;
      } catch (ConfigurationError e) {
        return false;
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

    @Override
    public boolean getEmailAsUsername() {
        return "true".equalsIgnoreCase(getAttribute(SCIM_EMAIL_AS_USERNAME));
    }


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

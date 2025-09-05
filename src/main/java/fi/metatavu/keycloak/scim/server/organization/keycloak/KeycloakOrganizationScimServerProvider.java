package fi.metatavu.keycloak.scim.server.organization.keycloak;

import org.keycloak.models.RealmModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.organization.OrganizationProvider;
import fi.metatavu.keycloak.scim.server.organization.*;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import fi.metatavu.keycloak.scim.server.config.ConfigurationError;

public class KeycloakOrganizationScimServerProvider implements OrganizationScimServerProvider {

  protected final KeycloakSession session;

  public KeycloakOrganizationScimServerProvider(KeycloakSession session) {
    this.session = session;
  }

  @Override
  public OrganizationScimServer getScimServer(KeycloakSession session) {
    return new OrganizationScimServer(session) {
      @Override
      public OrganizationScimContext getScimContext(KeycloakSession session, String organizationId) {
        return createScimContext(session, organizationId);
      }
    };
  }

  private static OrganizationScimContext createScimContext(KeycloakSession session, String organizationId) {
    RealmModel realm = session.getContext().getRealm();
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    
    final OrganizationModel organization = session.getProvider(OrganizationProvider.class).getById(organizationId);
  
    if (organization == null) {
      throw new NotFoundException("Organization not found");
    }
    
    KeycloakContext context = session.getContext();
    context.setOrganization(organization);

    URI baseUri = session.getContext().getUri().getBaseUri().resolve(String.format("realms/%s/scim/v2/organizations/%s/", realm.getName(), organization.getId()));
    OrganizationScimConfig config = new OrganizationScimConfig() {
        @Override
        public String getAttribute(String attributeName) {
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
      };

    try {
      config.validateConfig();
    } catch (ConfigurationError e) {
      throw new InternalServerErrorException("Invalid SCIM configuration", e);
    }

    return new KeycloakOrganizationScimContext(
        baseUri,
        session,
        realm,
        config,
        organization);
    }

}

package fi.metatavu.keycloak.scim.server.organization.phasetwo;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import fi.metatavu.keycloak.scim.server.ScimServer;
import fi.metatavu.keycloak.scim.server.organization.*;
import io.phasetwo.service.model.OrganizationModel;
import io.phasetwo.service.model.OrganizationProvider;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import java.net.URI;
import fi.metatavu.keycloak.scim.server.config.ConfigurationError;

public class PhasetwoOrganizationScimServerProvider implements OrganizationScimServerProvider {

  protected final KeycloakSession session;

  public PhasetwoOrganizationScimServerProvider(KeycloakSession session) {
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

  public static final String PHASETWO_ORGANIZATION_SESSION_ATTRIBUTE = "ext-phasetwo-organization";
  
  private static OrganizationScimContext createScimContext(KeycloakSession session, String organizationId) {
    RealmModel realm = session.getContext().getRealm();
    if (realm == null) {
      throw new NotFoundException("Realm not found");
    }
    
    final OrganizationModel organization = session.getProvider(OrganizationProvider.class).getOrganizationById(realm, organizationId);
  
    if (organization == null) {
      throw new NotFoundException("Organization not found");
    }
    
    session.setAttribute(PHASETWO_ORGANIZATION_SESSION_ATTRIBUTE, organization);

    URI baseUri = session.getContext().getUri().getBaseUri().resolve(String.format("realms/%s/scim/v2/organizations/%s/", realm.getName(), organization.getId()));
    OrganizationScimConfig config = new OrganizationScimConfig() {
        @Override
        public String getAttribute(String attributeName) {
          return organization.getFirstAttribute(attributeName);
        }
      };

    try {
      config.validateConfig();
    } catch (ConfigurationError e) {
      throw new InternalServerErrorException("Invalid SCIM configuration", e);
    }

    return new PhasetwoOrganizationScimContext(
        baseUri,
        session,
        realm,
        config,
        organization);
    }

}

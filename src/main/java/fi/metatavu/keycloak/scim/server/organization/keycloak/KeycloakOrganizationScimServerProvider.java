package fi.metatavu.keycloak.scim.server.organization.keycloak;

import org.keycloak.models.RealmModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.organization.OrganizationProvider;
import fi.metatavu.keycloak.scim.server.organization.*;
import java.net.URI;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import fi.metatavu.keycloak.scim.server.config.ConfigurationError;

public class KeycloakOrganizationScimServerProvider implements OrganizationScimServerProvider {

  @Override
  public OrganizationScimServer getScimServer(KeycloakSession session) {
    return new OrganizationScimServer() {
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

    // Try ComponentModel config first, fall back to organization attributes
    OrganizationScimConfig config = OrganizationScimServer.loadComponentConfig(realm, organizationId);
    if (config == null) {
      config = new KeycloakOrganizationScimConfig(organization);
    }

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

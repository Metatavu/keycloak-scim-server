package fi.metatavu.keycloak.scim.server.organization.keycloak;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.Config.Scope;
import fi.metatavu.keycloak.scim.server.organization.*;

public class KeycloakOrganizationScimServerProviderFactory implements OrganizationScimServerProviderFactory {

  public static final String PROVIDER_ID = "default";

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public OrganizationScimServerProvider create(KeycloakSession session) {
    return new KeycloakOrganizationScimServerProvider(session);
  }

  @Override
  public void init(Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}
}

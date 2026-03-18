package fi.metatavu.keycloak.scim.server;

import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.models.KeycloakSession;

/**
 * SCIM realm resource provider
 */
public class ScimRealmResourceProvider implements RealmResourceProvider {

  private final KeycloakSession session;
  private final String organizationType;
  
  public ScimRealmResourceProvider(KeycloakSession session, String organizationType) {
    this.session = session;
    this.organizationType = organizationType;
  }
  
  @Override
  public Object getResource() {
    return new ScimResources(session, organizationType);
  }

  @Override
  public void close() {
  }

}

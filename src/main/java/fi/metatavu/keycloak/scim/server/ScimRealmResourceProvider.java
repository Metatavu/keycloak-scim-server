package fi.metatavu.keycloak.scim.server;

import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.models.KeycloakSession;

/**
 * SCIM realm resource provider
 */
public class ScimRealmResourceProvider implements RealmResourceProvider {

  private final KeycloakSession session;

  public ScimRealmResourceProvider(KeycloakSession session) {
    this.session = session;
  }

  @Override
  public Object getResource() {
    return new ScimResources(session);
  }

  @Override
  public void close() {
  }

}

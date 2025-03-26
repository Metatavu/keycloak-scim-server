package fi.metatavu.keycloak.scim.server;

import org.keycloak.services.resource.RealmResourceProvider;

/**
 * SCIM realm resource provider
 */
public class ScimRealmResourceProvider implements RealmResourceProvider {

  @Override
  public Object getResource() {
    return new ScimResources();
  }

  @Override
  public void close() {
  }

}

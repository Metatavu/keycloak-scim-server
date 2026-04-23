package fi.metatavu.keycloak.scim.server.organization;

import org.keycloak.provider.Provider;
import org.keycloak.models.KeycloakSession;

public interface OrganizationScimServerProvider extends Provider {

  public OrganizationScimServer getScimServer(KeycloakSession session);

  default void close() {}
}

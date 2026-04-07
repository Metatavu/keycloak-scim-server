package fi.metatavu.keycloak.scim.server.organization;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.storage.UserStorageProvider;

/**
 * User storage provider for organization SCIM configuration.
 * This provider's sole purpose is to enable the Keycloak admin UI
 * to render a configuration form via the User Federation page.
 */
public class OrganizationScimConfigProvider implements UserStorageProvider {

    private final KeycloakSession session;
    private final ComponentModel model;

    public OrganizationScimConfigProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public void close() {
        // no-op
    }
}

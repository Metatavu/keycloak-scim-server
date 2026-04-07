package fi.metatavu.keycloak.scim.server.organization;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.RealmModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

/**
 * Factory for organization SCIM configuration provider.
 * Registers as a User Federation provider so Keycloak renders
 * a configuration GUI in the admin console.
 */
public class OrganizationScimConfigProviderFactory implements UserStorageProviderFactory<OrganizationScimConfigProvider> {

    private static final Logger logger = Logger.getLogger(OrganizationScimConfigProviderFactory.class.getName());

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ComponentScimConfig.getConfigProperties();
    }

    @Override
    public OrganizationScimConfigProvider create(KeycloakSession session, ComponentModel model) {
        return new OrganizationScimConfigProvider(session, model);
    }

    @Override
    public String getId() {
        return "Organization SCIM";
    }

    @Override
    public String getHelpText() {
        return "Organization SCIM v2 Configuration";
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) {
        logger.debug("OrganizationScimConfigProviderFactory validateConfiguration");

        ComponentScimConfig config = new ComponentScimConfig(model);

        if (model.getId() == null) {
            String organizationId = config.getOrganizationId();
            ComponentModel existing = realm.getComponent(organizationId);
            if (existing != null) {
                throw new ComponentValidationException(
                    "Another SCIM provider already exists for organization ID: " + organizationId
                );
            }
            config.setId(organizationId);
        }

        String orgId = config.getId();
        OrganizationProvider orgProvider = session.getProvider(OrganizationProvider.class);
        OrganizationModel organization = orgProvider.getById(orgId);

        if (organization == null) {
            throw new ComponentValidationException(
                "Organization not found: " + config.getOrganizationId()
            );
        }
    }

    @Override
    public void onCreate(KeycloakSession session, RealmModel realm, ComponentModel model) {
        logger.debug("OrganizationScimConfigProviderFactory onCreate");
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
        logger.debug("OrganizationScimConfigProviderFactory onUpdate");
    }
}

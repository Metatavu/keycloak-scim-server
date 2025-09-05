package fi.metatavu.keycloak.scim.server;

import com.google.common.base.Strings;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * SCIM realm resource provider factory
 * <p>
 * This class is responsible for creating SCIM realm resource providers
 */
public class ScimRealmResourceProviderFactory implements RealmResourceProviderFactory {

    private String organizationType = "default";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new ScimRealmResourceProvider(session, organizationType);
    }

    @Override
    public void init(Config.Scope config) {
        // allows overriding the default organization type with 'phasetwo'
        String orgTypeConfig = config.get("organizationType");
        if (!Strings.isNullOrEmpty(orgTypeConfig)) organizationType = orgTypeConfig;
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return "scim";
    }

}

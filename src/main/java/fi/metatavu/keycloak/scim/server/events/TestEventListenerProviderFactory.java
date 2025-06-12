package fi.metatavu.keycloak.scim.server.events;

import org.jboss.logmanager.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Test event listener provider factory that creates a test event listener provider
 * <p>
 * This is intended only for debugging purposes and should not be used in production.
 */
public class TestEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger logger = Logger.getLogger(TestEventListenerProviderFactory.class.getName());

    @Override
    public EventListenerProvider create(KeycloakSession keycloakSession) {
        logger.warning("This is a test event listener provider. It should not be used in production!");
        return new TestEventListenerProvider();
    }

    @Override
    public void init(Config.Scope scope) {

    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return "test-event-listener";
    }
}

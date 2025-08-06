package fi.metatavu.keycloak.scim.server.test.tests;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.testcontainers.junit.jupiter.Container;

/**
 * Abstract base class for realm level SCIM tests using internal Keycloak authentication
 */
public class AbstractInternalAuthRealmScimTest extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createInternalAuthRealmKeycloakContainer(network);

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @AfterAll
    static void tearDown() {
        KeycloakTestUtils.stopKeycloakContainer(keycloakContainer);
    }

}

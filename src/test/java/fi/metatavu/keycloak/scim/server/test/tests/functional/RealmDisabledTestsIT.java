package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 Schemas endpoint
 */
@Testcontainers
public class RealmDisabledTestsIT extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createNoAuthRealmKeycloakContainer(network);

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @AfterAll
    static void tearDown() {
        KeycloakTestUtils.stopKeycloakContainer(keycloakContainer);
    }

    @Test
    void testRealmScimDisabled() {
        ScimClient scimClient = getAuthenticatedScimClient();

        ApiException e = assertThrows(ApiException.class, scimClient::getSchemas);

        assertEquals("listSchemas call failed with: 500 - {\"error\":\"Invalid SCIM configuration\",\"error_description\":\"For more on this error consult the server log.\"}", e.getMessage());
    }

}
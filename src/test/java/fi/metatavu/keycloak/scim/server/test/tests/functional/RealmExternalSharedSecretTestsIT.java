package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
public class RealmExternalSharedSecretTestsIT extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createExternalAuthSharedSecretRealmKeycloakContainer(network);


    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @AfterAll
    static void tearDown() {
        KeycloakTestUtils.stopKeycloakContainer(keycloakContainer);
    }

    @Test
    void testGetResourceTypesWithExternalToken() throws ApiException {
        ScimClient scimClient = new ScimClient(getScimUri(), "tutu");
        scimClient.getResourceTypes();
    }

    @Test
    void testErrorGetResourceTypesWithExternalToken() {
        assertThrows(
            ApiException.class, () -> {
                ScimClient scimClient = new ScimClient(getScimUri(), "titi");
                scimClient.getResourceTypes();
            }
        );
    }
}

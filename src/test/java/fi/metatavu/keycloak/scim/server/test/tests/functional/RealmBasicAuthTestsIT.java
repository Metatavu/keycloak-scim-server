package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Basic auth tests are disabled on Keycloak 26.6.x due to a regression in RealmsResource.resolveRealmExtension()
 * that rejects non-Bearer Authorization headers before the request reaches realm resource extensions.
 * This breaks all Basic auth (e.g. Okta SCIM provisioning) in EXTERNAL authentication mode.
 *
 * Upstream issue: https://github.com/keycloak/keycloak/issues/49611
 * Fixed in: Keycloak 26.7.0
 *
 * Re-enable this test class when upgrading to Keycloak 26.7.0 or later.
 */
@Testcontainers
public class RealmBasicAuthTestsIT extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createBasicAuthRealmKeycloakContainer(network);

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @AfterAll
    static void tearDown() {
        KeycloakTestUtils.stopKeycloakContainer(keycloakContainer);
    }

    @Test
    void testGetResourceTypesWithBasicAuth() throws ApiException {
        ScimClient scimClient = new ScimClient(getScimUri(), "scim-admin", "tutu");
        scimClient.getResourceTypes();
    }

    @Test
    void testErrorGetResourceTypesWithWrongPassword() {
        assertThrows(
            ApiException.class, () -> {
                ScimClient scimClient = new ScimClient(getScimUri(), "scim-admin", "wrong-password");
                scimClient.getResourceTypes();
            }
        );
    }

    @Test
    void testErrorGetResourceTypesWithWrongUsername() {
        assertThrows(
            ApiException.class, () -> {
                ScimClient scimClient = new ScimClient(getScimUri(), "wrong-user", "tutu");
                scimClient.getResourceTypes();
            }
        );
    }
}

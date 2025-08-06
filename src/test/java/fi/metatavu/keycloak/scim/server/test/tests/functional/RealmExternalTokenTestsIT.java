package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests for SCIM 2.0 user find (GET /Users/{id}) endpoint
 */
@Testcontainers
public class RealmExternalTokenTestsIT extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createExternalAuthRealmKeycloakContainer(network);


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
        ScimClient scimClient = new ScimClient(getScimUri(), getExternalServiceAccountToken());
        scimClient.getResourceTypes();
    }

    /**
     * Returns access token for external service account
     *
     * @return access token
     */
    private String getExternalServiceAccountToken() {
        try (Keycloak keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(getKeycloakContainer().getAuthServerUrl())
                .realm(TestConsts.EXTERNAL_REALM)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(TestConsts.EXTERNAL_CLIENT_ID)
                .clientSecret(TestConsts.EXTERNAL_CLIENT_SECRET)
                .build()) {

            return keycloakAdmin
                    .tokenManager()
                    .getAccessToken()
                    .getToken();
        }
    }

}
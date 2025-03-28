package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
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
public class ExternalTokenTestsIT extends AbstractScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
        .withNetwork(network)
        .withNetworkAliases("scim-keycloak")
        .withEnv("SCIM_AUTHENTICATION_MODE", "EXTERNAL")
        .withEnv("SCIM_EXTERNAL_ISSUER", "*") // Just for testing purposes
        .withEnv("SCIM_EXTERNAL_AUDIENCE", "account")
        .withEnv("SCIM_EXTERNAL_JWKS_URI", "http://localhost:8080/realms/external/protocol/openid-connect/certs")
        .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
        .withRealmImportFiles("kc-test.json", "kc-external.json")
        .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));


    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
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
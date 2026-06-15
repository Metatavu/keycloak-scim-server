package fi.metatavu.keycloak.scim.server.test.tests;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.testcontainers.junit.jupiter.Container;

import java.net.URI;

/**
 * Abstract base class for organization SCIM tests that require editUsernameAllowed=true.
 */
public abstract class AbstractValidationOrganizationScimTest extends AbstractScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createValidationOrganizationKeycloakContainer(network);

    @AfterAll
    static void tearDown() {
        KeycloakTestUtils.stopKeycloakContainer(keycloakContainer);
    }

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    protected URI getScimUri(String organizationId) {
        return URI.create(getKeycloakContainer().getAuthServerUrl())
            .resolve(String.format("/realms/%s/scim/v2/organizations/%s/", TestConsts.ORGANIZATIONS_REALM, organizationId));
    }

    protected ScimClient getAuthenticatedScimClient(String organizationId) {
        return new ScimClient(getScimUri(organizationId), getExternalServiceAccountToken());
    }

    protected String getExternalServiceAccountToken() {
        try (Keycloak keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(getKeycloakContainer().getAuthServerUrl())
                .realm(TestConsts.EXTERNAL_REALM)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(TestConsts.EXTERNAL_CLIENT_ID)
                .clientSecret(TestConsts.EXTERNAL_CLIENT_SECRET)
                .build()) {
            return keycloakAdmin.tokenManager().getAccessToken().getToken();
        }
    }

}

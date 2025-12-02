package fi.metatavu.keycloak.scim.server.test.tests;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.util.JsonSerialization;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Abstract base class for organization SCIM tests
 */
public abstract class AbstractOrganizationScimTest extends AbstractScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createOrganizationKeycloakContainer(network);

    @AfterAll
    static void tearDown() {
        KeycloakTestUtils.stopKeycloakContainer(keycloakContainer);
    }

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    /**
     * Returns SCIM URI for the test realm
     *
     * @return SCIM URI
     */
    protected URI getScimUri(String organizationId) {
        return URI.create(getKeycloakContainer().getAuthServerUrl()).resolve(String.format("/realms/%s/scim/v2/organizations/%s/", TestConsts.ORGANIZATIONS_REALM, organizationId));
    }

    /**
     * Returns authenticated SCIM client
     *
     * @return authenticated SCIM client
     */
    protected ScimClient getAuthenticatedScimClient(String organizationId) {
        return getAuthenticatedSharedSecretScimClient(organizationId, getExternalServiceAccountToken());
    }

    /**
     * Returns authenticated SCIM client with shared secret
     *
     * @return authenticated SCIM client
     */
    protected ScimClient getAuthenticatedSharedSecretScimClient(String organizationId, String accessToken) {
        return new ScimClient(getScimUri(organizationId), accessToken);
    }

    /**
     * Returns access token for external service account
     *
     * @return access token
     */
    protected String getExternalServiceAccountToken() {
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

    /**
     * Asserts that the given organization member admin event matches the expected values
     *
     * @param userEvent the organization member admin event to assert
     * @param realmName the name of the realm
     * @param realmId the ID of the realm
     * @param userId the ID of the user
     * @param organizationId the ID of the organization
     * @param operationType the operation type of the event
     * @throws IOException if there is an error reading the organization representation
     */
    @SuppressWarnings("SameParameterValue")
    protected void assertOrganizationMemberEvent(
            AdminEvent userEvent,
            String realmName,
            String realmId,
            String userId,
            String organizationId,
            OperationType operationType
    ) throws IOException {
        assertNotNull(userEvent);
        assertEquals(realmId, userEvent.getRealmId());
        assertEquals(realmName, userEvent.getRealmName());
        assertEquals(ResourceType.ORGANIZATION_MEMBERSHIP, userEvent.getResourceType());
        assertEquals(operationType, userEvent.getOperationType());

        if (operationType == OperationType.CREATE) {
            assertEquals("organizations/" + organizationId + "/members", userEvent.getResourcePath());
        } else {
            assertEquals("organizations/" + organizationId + "/members/" + userId, userEvent.getResourcePath());
        }

        if (operationType != OperationType.DELETE) {
            UserRepresentation realmUser = findRealmUser(realmName, userId);
            assertEquals(realmUser.getUsername(), userEvent.getDetails().get("username"));
            assertEquals(realmUser.getEmail(), userEvent.getDetails().get("email"));
        }

        assertEquals("ORGANIZATION_MEMBERSHIP", userEvent.getResourceTypeAsString());

        OrganizationRepresentation eventOrganization = JsonSerialization.readValue(userEvent.getRepresentation(), OrganizationRepresentation.class);
        assertNotNull(eventOrganization);

        assertEquals(organizationId, eventOrganization.getId());
    }

}

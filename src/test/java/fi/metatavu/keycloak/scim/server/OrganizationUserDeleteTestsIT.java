package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User delete endpoint
 */
@Testcontainers
public class OrganizationUserDeleteTestsIT extends AbstractOrganizationScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
        .withNetwork(network)
        .withNetworkAliases("scim-keycloak")
        .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
        .withRealmImportFiles("kc-organizations.json", "kc-external.json")
        .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @Test
    void testDeleteUser() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        // Create user
        User created = createUser(scimClient, "delete-me", "Delete", "Me");

        // Assert that the user can be found from organization 1
        assertNotNull(findOrganizationMember(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, created.getId()));

        // Delete user
        scimClient.deleteUser(created.getId());

        // Assert that the user cannot be found from organization 1 anymore
        assertThrows(NotFoundException.class, () ->
            findOrganizationMember(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, created.getId())
        );

        // Try to fetch the user to confirm deletion
        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.findUser(created.getId())
        );

        assertEquals(404, exception.getCode());

        assertThrows(NotFoundException.class, () ->
            findRealmUser(TestConsts.TEST_REALM, created.getId())
        );
    }

    @Test
    void testDeleteNonexistentUserReturns404() {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        String nonexistentId = "nonexistent-user-id";

        ApiException exception = assertThrows(ApiException.class, () ->
                scimClient.deleteUser(nonexistentId)
        );

        assertEquals(404, exception.getCode());
    }

}
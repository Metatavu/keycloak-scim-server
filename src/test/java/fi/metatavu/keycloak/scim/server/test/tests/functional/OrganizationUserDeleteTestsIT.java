package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;

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
        .withFileSystemBind("build/testdata", "/tmp/testdata", BindMode.READ_WRITE)
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

    @Test
    void testDeleteUserAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        User user = createUser(scimClient, "delete-me", "Delete", "Me");
        clearAdminEvents();

        // Delete user
        scimClient.deleteUser(user.getId());

        List<AdminEvent> adminEvents = getAdminEvents();
        assertEquals(2, adminEvents.size());

        AdminEvent deleteUserEvent = adminEvents.stream()
                .filter(event -> event.getResourceType() == ResourceType.USER)
                .findFirst()
                .orElse(null);

        assertUserAdminEvent(
                deleteUserEvent,
                TestConsts.ORGANIZATIONS_REALM,
                TestConsts.ORGANIZATIONS_REALM_ID,
                user.getId(),
                OperationType.DELETE
        );

        AdminEvent removeMemberEvent = adminEvents.stream()
                .filter(event -> event.getResourceType() == ResourceType.ORGANIZATION_MEMBERSHIP)
                .findFirst()
                .orElse(null);

        assertOrganizationMemberEvent(
                removeMemberEvent,
                TestConsts.ORGANIZATIONS_REALM,
                TestConsts.ORGANIZATIONS_REALM_ID,
                user.getId(),
                TestConsts.ORGANIZATION_1_ID,
                OperationType.DELETE
        );
    }

}
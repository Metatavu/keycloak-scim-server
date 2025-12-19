package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.Group;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 Group delete endpoint and admin events
 */
@Testcontainers
public class RealmGroupDeleteTestsIT extends AbstractInternalAuthRealmScimTest {

    @Test
    void testDeleteGroup() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create a group to delete
        Group group = createGroup(scimClient, "group-to-delete");

        // Delete the group
        scimClient.deleteGroup(group.getId());

        // Verify the group was deleted from Keycloak
        assertThrows(Exception.class, () -> findRealmGroup(TestConsts.TEST_REALM, group.getId()));
    }

    @Test
    void testDeleteNonExistentGroupReturnsNotFound() {
        ScimClient scimClient = getAuthenticatedScimClient();

        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.deleteGroup("non-existent-group-id")
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void testDeleteGroupAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create a group to delete
        Group group = createGroup(scimClient, "event-delete-group");

        // Clear admin events from creation
        clearAdminEvents();

        // Delete the group
        scimClient.deleteGroup(group.getId());

        List<AdminEvent> adminEvents = getAdminEvents();
        assertEquals(1, adminEvents.size());

        AdminEvent deleteGroupEvent = adminEvents.stream()
            .filter(event -> event.getResourceType() == ResourceType.GROUP)
            .findFirst()
            .orElse(null);

        assertNotNull(deleteGroupEvent);
        assertEquals(TestConsts.TEST_REALM_ID, deleteGroupEvent.getRealmId());
        assertEquals(TestConsts.TEST_REALM, deleteGroupEvent.getRealmName());
        assertEquals(ResourceType.GROUP, deleteGroupEvent.getResourceType());
        assertEquals(OperationType.DELETE, deleteGroupEvent.getOperationType());
        assertEquals("groups/" + group.getId(), deleteGroupEvent.getResourcePath());
        assertEquals("GROUP", deleteGroupEvent.getResourceTypeAsString());
    }
}

package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.Group;
import fi.metatavu.keycloak.scim.server.test.client.model.GroupMembersInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.representations.idm.GroupRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 Group create endpoint and admin events
 */
@Testcontainers
public class RealmGroupCreateTestsIT extends AbstractInternalAuthRealmScimTest {

    @Test
    void testCreateGroup() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = new Group();
        group.setDisplayName("test-group");
        group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));

        Group created = scimClient.createGroup(group);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("test-group", created.getDisplayName());
        assertEquals(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"), created.getSchemas());

        // Assert that the group was created in Keycloak
        GroupRepresentation realmGroup = findRealmGroup(TestConsts.TEST_REALM, created.getId());
        assertNotNull(realmGroup);
        assertEquals("test-group", realmGroup.getName());

        // Clean up
        deleteRealmGroup(TestConsts.TEST_REALM, realmGroup.getId());
    }

    /**
     * Members that SCIM does not manage (users created outside SCIM, service accounts)
     * are dropped on create like unknown ids.
     */
    @Test
    void testCreateGroupIgnoresUnmanagedMembers() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User managed = createUser(scimClient, "create-managed-member", "Create", "Managed");
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "create-unmanaged-member", "Create", "Unmanaged");
        String createdGroupId = null;

        try {
            Group group = new Group();
            group.setDisplayName("create-unmanaged-group");
            group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));
            group.setMembers(List.of(memberRef(managed.getId()), memberRef(unmanagedUserId),
                    memberRef("7437c9e2-f7de-4bb1-b52f-6a6a61a598ab")));

            Group created = scimClient.createGroup(group);
            createdGroupId = created.getId();

            assertNotNull(created.getMembers());
            assertEquals(List.of(managed.getId()), created.getMembers().stream().map(GroupMembersInner::getValue).toList());
            assertEquals(Set.of(managed.getId()), getRealmGroupMemberIds(TestConsts.TEST_REALM, createdGroupId));
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, managed.getId());
            deleteRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
            if (createdGroupId != null) {
                deleteRealmGroup(TestConsts.TEST_REALM, createdGroupId);
            }
        }
    }

    /**
     * Builds a group member reference
     *
     * @param userId user ID
     * @return member reference
     */
    private GroupMembersInner memberRef(String userId) {
        GroupMembersInner ref = new GroupMembersInner();
        ref.setValue(userId);
        return ref;
    }

    @Test
    void testCreateGroupAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = new Group();
        group.setDisplayName("event-test-group");
        group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));

        group = scimClient.createGroup(group);

        List<AdminEvent> adminEvents = getAdminEvents();
        assertEquals(1, adminEvents.size());

        AdminEvent createGroupEvent = adminEvents.stream()
            .filter(event -> event.getResourceType() == ResourceType.GROUP)
            .findFirst()
            .orElse(null);

        assertGroupAdminEvent(
            createGroupEvent,
            TestConsts.TEST_REALM,
            TestConsts.TEST_REALM_ID,
            group.getId(),
            OperationType.CREATE
        );

        // Clean up
        deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
    }
  
    @Test
    void testCreateGroupWithoutDisplayNameReturnsBadRequest() {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = new Group();
        group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));

        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.createGroup(group)
        );

        assertEquals(400, exception.getCode());
    }

    @Test
    void testCreateGroupWithBlankDisplayNameReturnsBadRequest() {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = new Group();
        group.setDisplayName("   ");
        group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));

        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.createGroup(group)
        );

        assertEquals(400, exception.getCode());
    }
}

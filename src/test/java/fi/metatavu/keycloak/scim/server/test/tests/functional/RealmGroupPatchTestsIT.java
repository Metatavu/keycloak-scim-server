package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.*;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 Group patch endpoint and group membership admin events
 */
@Testcontainers
public class RealmGroupPatchTestsIT extends AbstractInternalAuthRealmScimTest {

    @Test
    void testAddGroupMember() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create a user and a group
        User user = createUser(scimClient, "test-user", "Test", "User");
        Group group = createGroup(scimClient, "test-group");

        // Add the user to the group via PATCH
        PatchRequest patchRequest = new PatchRequest();
        patchRequest.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));

        PatchRequestOperationsInner operation = new PatchRequestOperationsInner();
        operation.setOp("add");
        operation.setPath("members");

        GroupMembersInner member = new GroupMembersInner();
        member.setValue(user.getId());
        operation.setValue(Collections.singletonList(member));

        patchRequest.setOperations(List.of(operation));

        Group patched = scimClient.patchGroup(group.getId(), patchRequest);

        // Verify the user is a member of the group
        assertNotNull(patched.getMembers());
        assertEquals(1, patched.getMembers().size());
        assertEquals(user.getId(), patched.getMembers().get(0).getValue());

        // Clean up
        deleteRealmUser(TestConsts.TEST_REALM, user.getId());
        deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
    }

    @Test
    void testRemoveGroupMember() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create a user and a group
        User user = createUser(scimClient, "test-user-2", "Test", "User");
        Group group = createGroup(scimClient, "test-group-2");

        // Add the user to the group
        PatchRequest addRequest = new PatchRequest();
        addRequest.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));

        PatchRequestOperationsInner addOperation = new PatchRequestOperationsInner();
        addOperation.setOp("add");
        addOperation.setPath("members");

        GroupMembersInner member = new GroupMembersInner();
        member.setValue(user.getId());
        addOperation.setValue(Collections.singletonList(member));

        addRequest.setOperations(List.of(addOperation));
        scimClient.patchGroup(group.getId(), addRequest);

        // Remove the user from the group
        PatchRequest removeRequest = new PatchRequest();
        removeRequest.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));

        PatchRequestOperationsInner removeOperation = new PatchRequestOperationsInner();
        removeOperation.setOp("remove");
        removeOperation.setPath("members[value eq \"" + user.getId() + "\"]");

        removeRequest.setOperations(List.of(removeOperation));

        Group patched = scimClient.patchGroup(group.getId(), removeRequest);

        // Verify the user is no longer a member of the group
        assertTrue(patched.getMembers() == null || patched.getMembers().isEmpty());

        // Clean up
        deleteRealmUser(TestConsts.TEST_REALM, user.getId());
        deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
    }

    @Test
    void testAddGroupMemberAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create a user and a group
        User user = createUser(scimClient, "event-test-user", "Event", "User");
        Group group = createGroup(scimClient, "event-test-group");

        // Clear admin events from creation
        clearAdminEvents();

        // Add the user to the group via PATCH
        PatchRequest patchRequest = new PatchRequest();
        patchRequest.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));

        PatchRequestOperationsInner operation = new PatchRequestOperationsInner();
        operation.setOp("add");
        operation.setPath("members");

        GroupMembersInner member = new GroupMembersInner();
        member.setValue(user.getId());
        operation.setValue(Collections.singletonList(member));

        patchRequest.setOperations(List.of(operation));

        scimClient.patchGroup(group.getId(), patchRequest);

        List<AdminEvent> adminEvents = getAdminEvents();
        assertEquals(1, adminEvents.size());

        AdminEvent membershipEvent = adminEvents.stream()
            .filter(event -> event.getResourceType() == ResourceType.GROUP_MEMBERSHIP)
            .findFirst()
            .orElse(null);

        assertGroupMembershipAdminEvent(
            membershipEvent,
            TestConsts.TEST_REALM,
            TestConsts.TEST_REALM_ID,
            group.getId(),
            user.getId(),
            OperationType.CREATE
        );

        // Clean up
        deleteRealmUser(TestConsts.TEST_REALM, user.getId());
        deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
    }

    @Test
    void testRemoveGroupMemberAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create a user and a group
        User user = createUser(scimClient, "event-remove-user", "Event", "User");
        Group group = createGroup(scimClient, "event-remove-group");

        // Add the user to the group
        PatchRequest addRequest = new PatchRequest();
        addRequest.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));

        PatchRequestOperationsInner addOperation = new PatchRequestOperationsInner();
        addOperation.setOp("add");
        addOperation.setPath("members");

        GroupMembersInner member = new GroupMembersInner();
        member.setValue(user.getId());
        addOperation.setValue(Collections.singletonList(member));

        addRequest.setOperations(List.of(addOperation));
        scimClient.patchGroup(group.getId(), addRequest);

        // Clear admin events from creation and membership add
        clearAdminEvents();

        // Remove the user from the group
        PatchRequest removeRequest = new PatchRequest();
        removeRequest.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));

        PatchRequestOperationsInner removeOperation = new PatchRequestOperationsInner();
        removeOperation.setOp("remove");
        removeOperation.setPath("members[value eq \"" + user.getId() + "\"]");

        removeRequest.setOperations(List.of(removeOperation));

        scimClient.patchGroup(group.getId(), removeRequest);

        List<AdminEvent> adminEvents = getAdminEvents();
        assertEquals(1, adminEvents.size());

        AdminEvent membershipEvent = adminEvents.stream()
            .filter(event -> event.getResourceType() == ResourceType.GROUP_MEMBERSHIP)
            .findFirst()
            .orElse(null);

        assertGroupMembershipAdminEvent(
            membershipEvent,
            TestConsts.TEST_REALM,
            TestConsts.TEST_REALM_ID,
            group.getId(),
            user.getId(),
            OperationType.DELETE
        );

        // Clean up
        deleteRealmUser(TestConsts.TEST_REALM, user.getId());
        deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
    }
}

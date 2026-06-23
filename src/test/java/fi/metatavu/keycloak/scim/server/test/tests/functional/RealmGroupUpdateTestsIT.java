package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.Group;
import fi.metatavu.keycloak.scim.server.test.client.model.GroupMembersInner;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.representations.idm.GroupRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SCIM 2.0 Group {@code PUT /Groups/{id}} endpoint (full-resource update).
 *
 * <p>Covers the two fixes in this PR:
 * <ul>
 *   <li>Reconciling the request's {@code members} against the current members
 *       (Okta's Group Push wire shape).</li>
 *   <li>Evicting the affected users from Keycloak's {@code UserCache} so a
 *       subsequent admin-API read of {@code /users/{id}/groups} returns a
 *       fresh view rather than the cached pre-change list.</li>
 * </ul>
 */
@Testcontainers
public class RealmGroupUpdateTestsIT extends AbstractInternalAuthRealmScimTest {

    private static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

    @Test
    void testUpdateGroupAddsMembersOnEmptyGroup() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User user = createUser(scimClient, "put-add-user", "Put", "Add");
        Group group = createGroup(scimClient, "put-add-group");

        try {
            Group updated = scimClient.updateGroup(group.getId(),
                    groupUpdate(group.getDisplayName(), user.getId()));

            assertMembersEqual(Set.of(user.getId()), updated.getMembers());

            Group after = scimClient.findGroup(group.getId());
            assertMembersEqual(Set.of(user.getId()), after.getMembers());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    @Test
    void testUpdateGroupReplacesMembers() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User keep = createUser(scimClient, "put-replace-keep", "Put", "Keep");
        User remove = createUser(scimClient, "put-replace-remove", "Put", "Remove");
        User add = createUser(scimClient, "put-replace-add", "Put", "Add");
        Group group = createGroup(scimClient, "put-replace-group");

        try {
            // Seed [keep, remove] via PATCH so PUT exercises an actual diff.
            seedGroupMembers(scimClient, group, keep, remove);

            Group updated = scimClient.updateGroup(group.getId(),
                    groupUpdate(group.getDisplayName(), keep.getId(), add.getId()));

            assertMembersEqual(Set.of(keep.getId(), add.getId()), updated.getMembers());

            Group after = scimClient.findGroup(group.getId());
            assertMembersEqual(Set.of(keep.getId(), add.getId()), after.getMembers());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, keep.getId());
            deleteRealmUser(TestConsts.TEST_REALM, remove.getId());
            deleteRealmUser(TestConsts.TEST_REALM, add.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    @Test
    void testUpdateGroupWithEmptyMembersRemovesAll() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User user = createUser(scimClient, "put-clear-user", "Put", "Clear");
        Group group = createGroup(scimClient, "put-clear-group");

        try {
            seedGroupMembers(scimClient, group, user);

            Group update = new Group();
            update.setSchemas(List.of(GROUP_SCHEMA));
            update.setDisplayName(group.getDisplayName());
            update.setMembers(Collections.emptyList());

            Group updated = scimClient.updateGroup(group.getId(), update);

            assertTrue(updated.getMembers() == null || updated.getMembers().isEmpty(),
                    "PUT with members=[] must remove all members");

            Group after = scimClient.findGroup(group.getId());
            assertTrue(after.getMembers() == null || after.getMembers().isEmpty(),
                    "GET after empty-members PUT must show no members");
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    @Test
    void testUpdateGroupWithNullMembersLeavesMembershipUntouched() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User user = createUser(scimClient, "put-null-members-user", "Put", "NullMembers");
        Group group = createGroup(scimClient, "put-null-members-group");

        try {
            seedGroupMembers(scimClient, group, user);

            // Members omitted (null) — only displayName is changing.
            Group update = new Group();
            update.setSchemas(List.of(GROUP_SCHEMA));
            update.setDisplayName("renamed-null-members-group");

            Group updated = scimClient.updateGroup(group.getId(), update);

            assertEquals("renamed-null-members-group", updated.getDisplayName());
            assertMembersEqual(Set.of(user.getId()), updated.getMembers());

            Group after = scimClient.findGroup(group.getId());
            assertEquals("renamed-null-members-group", after.getDisplayName());
            assertMembersEqual(Set.of(user.getId()), after.getMembers());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    @Test
    void testUpdateGroupWithNullDisplayNameKeepsName() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User user = createUser(scimClient, "put-null-name-user", "Put", "NullName");
        Group group = createGroup(scimClient, "put-null-name-group");

        try {
            // Members only, displayName omitted (null) — name must be preserved.
            Group update = new Group();
            update.setSchemas(List.of(GROUP_SCHEMA));
            update.setMembers(List.of(memberRef(user.getId())));

            Group updated = scimClient.updateGroup(group.getId(), update);

            assertEquals("put-null-name-group", updated.getDisplayName(),
                    "PUT without displayName must not blank the name");
            assertMembersEqual(Set.of(user.getId()), updated.getMembers());

            Group after = scimClient.findGroup(group.getId());
            assertEquals("put-null-name-group", after.getDisplayName());
            assertMembersEqual(Set.of(user.getId()), after.getMembers());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    @Test
    void testUpdateGroupDispatchesMembershipEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User outgoing = createUser(scimClient, "put-event-out", "Event", "Out");
        User incoming = createUser(scimClient, "put-event-in", "Event", "In");
        Group group = createGroup(scimClient, "put-event-group");

        try {
            seedGroupMembers(scimClient, group, outgoing);
            // Drop create + seed events so we only see what PUT emits.
            clearAdminEvents();

            scimClient.updateGroup(group.getId(),
                    groupUpdate(group.getDisplayName(), incoming.getId()));

            List<AdminEvent> membershipEvents = getAdminEvents().stream()
                    .filter(e -> e.getResourceType() == ResourceType.GROUP_MEMBERSHIP)
                    .toList();

            assertEquals(2, membershipEvents.size(),
                    "Expected one DELETE (outgoing) and one CREATE (incoming) membership event");

            AdminEvent leaveEvent = membershipEvents.stream()
                    .filter(e -> e.getOperationType() == OperationType.DELETE)
                    .findFirst()
                    .orElse(null);
            assertGroupMembershipAdminEvent(
                    leaveEvent,
                    TestConsts.TEST_REALM,
                    TestConsts.TEST_REALM_ID,
                    group.getId(),
                    outgoing.getId(),
                    OperationType.DELETE
            );

            AdminEvent joinEvent = membershipEvents.stream()
                    .filter(e -> e.getOperationType() == OperationType.CREATE)
                    .findFirst()
                    .orElse(null);
            assertGroupMembershipAdminEvent(
                    joinEvent,
                    TestConsts.TEST_REALM,
                    TestConsts.TEST_REALM_ID,
                    group.getId(),
                    incoming.getId(),
                    OperationType.CREATE
            );
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, outgoing.getId());
            deleteRealmUser(TestConsts.TEST_REALM, incoming.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Bug 2: after a PUT removes a user from a group, the admin-side
     * {@code GET /users/{id}/groups} must reflect the change immediately —
     * not return a stale cached list.
     */
    @Test
    void testUpdateGroupRefreshesUserGroupsViewAfterRemove() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User user = createUser(scimClient, "put-cache-remove-user", "Put", "CacheRemove");
        Group group = createGroup(scimClient, "put-cache-remove-group");

        try {
            seedGroupMembers(scimClient, group, user);

            // Prime the UserCache by reading the user's groups via the admin API
            // before the change. Without this, the post-change read would hit the
            // DB on a cold cache and pass even without the eviction fix.
            assertTrue(userGroupNames(user.getId()).contains(group.getDisplayName()),
                    "Pre-condition: cache-priming read should show the seeded group");

            Group update = new Group();
            update.setSchemas(List.of(GROUP_SCHEMA));
            update.setDisplayName(group.getDisplayName());
            update.setMembers(Collections.emptyList());
            scimClient.updateGroup(group.getId(), update);

            assertFalse(userGroupNames(user.getId()).contains(group.getDisplayName()),
                    "admin /users/{id}/groups must not return the group after PUT removes membership");
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Bug 2 (symmetric): after a PUT adds a user to a group, the admin-side
     * {@code GET /users/{id}/groups} must reflect the new membership without
     * needing a manual cache clear.
     */
    @Test
    void testUpdateGroupRefreshesUserGroupsViewAfterAdd() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User user = createUser(scimClient, "put-cache-add-user", "Put", "CacheAdd");
        Group group = createGroup(scimClient, "put-cache-add-group");

        try {
            // Prime the cache with the user NOT in the group.
            assertFalse(userGroupNames(user.getId()).contains(group.getDisplayName()),
                    "Pre-condition: cache-priming read should show the user is not yet a member");

            scimClient.updateGroup(group.getId(),
                    groupUpdate(group.getDisplayName(), user.getId()));

            assertTrue(userGroupNames(user.getId()).contains(group.getDisplayName()),
                    "admin /users/{id}/groups must reflect the new membership after PUT");
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Behavior pin: unlike {@code PATCH}, which rejects unknown member IDs
     * atomically with a 400 ({@code InvalidGroupMemberReference}), {@code PUT}
     * currently silently drops them. This test locks in the current shape so a
     * future change to make PUT atomic deliberately breaks this assertion
     * rather than shifting semantics under the radar.
     */
    @Test
    void testUpdateGroupSilentlyIgnoresUnknownMemberIds() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User user = createUser(scimClient, "put-unknown-user", "Put", "Unknown");
        Group group = createGroup(scimClient, "put-unknown-group");

        try {
            GroupMembersInner unknown = new GroupMembersInner();
            unknown.setValue("00000000-0000-0000-0000-000000000000");

            Group update = new Group();
            update.setSchemas(List.of(GROUP_SCHEMA));
            update.setDisplayName(group.getDisplayName());
            update.setMembers(List.of(memberRef(user.getId()), unknown));

            Group updated = scimClient.updateGroup(group.getId(), update);

            assertMembersEqual(Set.of(user.getId()), updated.getMembers());

            Group after = scimClient.findGroup(group.getId());
            assertMembersEqual(Set.of(user.getId()), after.getMembers());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    // --- helpers ---

    private GroupMembersInner memberRef(String userId) {
        GroupMembersInner ref = new GroupMembersInner();
        ref.setValue(userId);
        return ref;
    }

    private Group groupUpdate(String displayName, String... memberIds) {
        Group update = new Group();
        update.setSchemas(List.of(GROUP_SCHEMA));
        update.setDisplayName(displayName);
        List<GroupMembersInner> members = Arrays.stream(memberIds)
                .map(this::memberRef)
                .collect(Collectors.toList());
        update.setMembers(members);
        return update;
    }

    /**
     * Seed a group with the given users via a PATCH add-members op. Used by
     * tests that need a non-empty starting state before exercising the PUT
     * code path.
     */
    private void seedGroupMembers(ScimClient scimClient, Group group, User... users) throws ApiException {
        PatchRequest seed = new PatchRequest();
        seed.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        PatchRequestOperationsInner addOp = new PatchRequestOperationsInner();
        addOp.setOp("add");
        addOp.setPath("members");
        List<GroupMembersInner> refs = Arrays.stream(users)
                .map(u -> memberRef(u.getId()))
                .collect(Collectors.toList());
        addOp.setValue(refs);
        seed.setOperations(List.of(addOp));
        scimClient.patchGroup(group.getId(), seed);
    }

    private void assertMembersEqual(Set<String> expectedIds, List<GroupMembersInner> actual) {
        assertNotNull(actual, "members must not be null");
        Set<String> actualIds = actual.stream()
                .map(GroupMembersInner::getValue)
                .collect(Collectors.toSet());
        assertEquals(expectedIds, actualIds, "group members mismatch");
    }

    /**
     * Returns the names of groups the given user belongs to, queried via the
     * Keycloak admin REST API. Reading this view is what exercises the
     * Infinispan user cache the PR's eviction fix targets.
     */
    private Set<String> userGroupNames(String userId) {
        List<GroupRepresentation> groups = getKeycloakContainer().getKeycloakAdminClient()
                .realms()
                .realm(TestConsts.TEST_REALM)
                .users()
                .get(userId)
                .groups();
        return groups.stream().map(GroupRepresentation::getName).collect(Collectors.toSet());
    }
}

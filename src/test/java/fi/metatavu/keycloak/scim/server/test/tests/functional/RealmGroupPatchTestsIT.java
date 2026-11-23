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
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 Group patch endpoint and group membership admin events
 */
@Testcontainers
public class RealmGroupPatchTestsIT extends AbstractInternalAuthRealmScimTest {

    /** service-account-scim-client from kc-test.json, which has no scim-managed role */
    private static final String SERVICE_ACCOUNT_USER_ID = "7437c9e2-f7de-4bb1-b52f-6a6a61a598ab";

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

        // A successful group PATCH answers 204 No Content (RFC 7644 §3.5.2);
        // membership is verified with a follow-up read.
        ScimClient.ScimResponse patched = scimClient.patchGroup(group.getId(), patchRequest);
        assertEquals(204, patched.status());
        assertTrue(patched.body() == null || patched.body().isEmpty());

        // Verify the user is a member of the group
        Group after = scimClient.findGroup(group.getId());
        assertNotNull(after.getMembers());
        assertEquals(1, after.getMembers().size());
        assertEquals(user.getId(), after.getMembers().get(0).getValue());

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

        assertEquals(204, scimClient.patchGroup(group.getId(), removeRequest).status());

        // Verify the user is no longer a member of the group
        Group after = scimClient.findGroup(group.getId());
        assertTrue(after.getMembers() == null || after.getMembers().isEmpty());

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

    /**
     * An ADD members operation that includes one valid and one unknown member ID
     * must be rejected atomically: HTTP 400 and the valid member must NOT sneak
     * into the group. Complements the REPLACE / REMOVE atomicity tests below.
     */
    @Test
    void testAddMembersRejectsUnknownIdWithoutMutation() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User seeded = createUser(scimClient, "atomic-add-seeded", "Atomic", "Seeded");
        User candidate = createUser(scimClient, "atomic-add-candidate", "Atomic", "Candidate");
        Group group = createGroup(scimClient, "atomic-add-group");

        try {
            // Seed with a known member so we can verify the group is left exactly
            // as it was (and the candidate did not sneak in).
            seedGroupWithMember(scimClient, group, seeded);

            // ADD [candidate, unknown] -- must fail atomically with HTTP 400.
            GroupMembersInner candidateRef = new GroupMembersInner();
            candidateRef.setValue(candidate.getId());
            GroupMembersInner unknown = new GroupMembersInner();
            unknown.setValue("22222222-2222-2222-2222-222222222222");
            PatchRequest add = new PatchRequest();
            add.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
            PatchRequestOperationsInner addOp = new PatchRequestOperationsInner();
            addOp.setOp("add");
            addOp.setPath("members");
            addOp.setValue(List.of(candidateRef, unknown));
            add.setOperations(List.of(addOp));

            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(), add));
            assertEquals(400, ex.getCode());

            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBody());
            assertEquals("400", body.get("status").asText());
            assertTrue(body.get("detail").asText().contains("22222222-2222-2222-2222-222222222222"),
                    "detail should name the unknown member id; got: " + body.get("detail").asText());

            // Group must be unchanged: only the seeded member, candidate did NOT sneak in.
            assertGroupHasOnlyMember(scimClient, group, seeded);
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, candidate.getId());
            deleteRealmUser(TestConsts.TEST_REALM, seeded.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * A REPLACE operation that includes one valid and one unknown member ID must
     * be rejected atomically: HTTP 400 with an error body naming the bad ID, and
     * the group's original membership must be unchanged.
     */
    @Test
    void testReplaceMembersRejectsUnknownIdWithoutMutation() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "atomic-1", "Atomic", "One");
        Group group = createGroup(scimClient, "atomic-group");

        try {
            // Seed with a known member
            seedGroupWithMember(scimClient, group, user);

            // REPLACE with [known, unknown] -- must fail atomically with HTTP 400.
            GroupMembersInner known = new GroupMembersInner();
            known.setValue(user.getId());
            GroupMembersInner unknown = new GroupMembersInner();
            unknown.setValue("00000000-0000-0000-0000-000000000000");
            PatchRequest replace = new PatchRequest();
            replace.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
            PatchRequestOperationsInner replaceOp = new PatchRequestOperationsInner();
            replaceOp.setOp("replace");
            replaceOp.setPath("members");
            replaceOp.setValue(List.of(known, unknown));
            replace.setOperations(List.of(replaceOp));

            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(), replace));
            assertEquals(400, ex.getCode());

            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBody());
            assertEquals("400", body.get("status").asText());
            assertTrue(body.get("detail").asText().contains("00000000-0000-0000-0000-000000000000"),
                    "detail should name the unknown member id; got: " + body.get("detail").asText());

            // Group state must be unchanged: original known member still present.
            assertGroupHasOnlyMember(scimClient, group, user);
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Same atomicity guarantee for the path-less PatchOp shape (Okta Group Push):
     * {"op":"replace","value":{"members":[...]}}. An unknown member ID must yield
     * HTTP 400 without mutating the group.
     */
    @Test
    void testReplaceMembersPathLessRejectsUnknownIdWithoutMutation() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "atomic-pathless-1", "Atomic", "Pathless");
        Group group = createGroup(scimClient, "atomic-pathless-group");

        try {
            // Seed with a known member via path-based ADD
            seedGroupWithMember(scimClient, group, user);

            // Path-less REPLACE with one known + one unknown member.
            // Shape: {"op":"replace","value":{"members":[{"value":"<known>"}, {"value":"<unknown>"}]}}
            Map<String, Object> knownMap = Map.of("value", user.getId());
            Map<String, Object> unknownMap = Map.of("value", "00000000-0000-0000-0000-000000000000");
            PatchRequest replace = new PatchRequest();
            replace.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
            PatchRequestOperationsInner replaceOp = new PatchRequestOperationsInner();
            replaceOp.setOp("replace");
            // No path -- value is a map of attribute -> list, Okta Group Push shape
            replaceOp.setValue(Map.of("members", List.of(knownMap, unknownMap)));
            replace.setOperations(List.of(replaceOp));

            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(), replace));
            assertEquals(400, ex.getCode());

            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBody());
            assertEquals("400", body.get("status").asText());
            assertTrue(body.get("detail").asText().contains("00000000-0000-0000-0000-000000000000"),
                    "detail should name the unknown member id; got: " + body.get("detail").asText());

            // Group state must be unchanged: original known member still present.
            assertGroupHasOnlyMember(scimClient, group, user);
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    @Test
    void testRemoveUnknownMemberIsRejected() throws ApiException, java.io.IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "remove-unknown-1", "Remove", "Unknown");
        Group group = createGroup(scimClient, "remove-unknown-group");

        try {
            // Seed with the known member
            seedGroupWithMember(scimClient, group, user);

            // REMOVE [unknown] should fail 400 atomically; the known member stays.
            GroupMembersInner unknown = new GroupMembersInner();
            unknown.setValue("11111111-1111-1111-1111-111111111111");
            PatchRequest remove = new PatchRequest();
            remove.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
            PatchRequestOperationsInner removeOp = new PatchRequestOperationsInner();
            removeOp.setOp("remove");
            removeOp.setPath("members");
            removeOp.setValue(List.of(unknown));
            remove.setOperations(List.of(removeOp));

            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(), remove));
            assertEquals(400, ex.getCode());
            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBody());
            assertTrue(body.get("detail").asText().contains("11111111-1111-1111-1111-111111111111"),
                    "detail should name the unknown member id; got: " + body.get("detail").asText());

            // Known member untouched
            assertGroupHasOnlyMember(scimClient, group, user);
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * REMOVE with an unquoted (malformed) filter value must return HTTP 400
     * with a SCIM error body, not silently succeed.
     * Example malformed path: members[value eq abc]  (no quotes around the id)
     */
    @Test
    void testRemoveMemberWithMalformedFilterReturns400() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "malformed-filter-user", "Malformed", "Filter");
        Group group = createGroup(scimClient, "malformed-filter-group");

        try {
            // Seed with a known member so the group is non-empty
            seedGroupWithMember(scimClient, group, user);

            // REMOVE with unquoted filter value -- must be rejected with 400.
            PatchRequest remove = new PatchRequest();
            remove.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
            PatchRequestOperationsInner removeOp = new PatchRequestOperationsInner();
            removeOp.setOp("remove");
            // Intentionally malformed: value not quoted
            removeOp.setPath("members[value eq " + user.getId() + "]");
            remove.setOperations(List.of(removeOp));

            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(), remove));
            assertEquals(400, ex.getCode());

            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBody());
            assertEquals("400", body.get("status").asText());

            // Group state must be unchanged: original member still present.
            assertGroupHasOnlyMember(scimClient, group, user);
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Okta Group Push wire shape: path-less PatchOp where members are nested under the value map.
     * Example body: {"op":"replace","value":{"members":[{"value":"<user-id>"}]}}
     */
    @Test
    void testAddMemberPathLessPatchOp() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "pathless-add-1", "Pathless", "Add");
        Group group = createGroup(scimClient, "pathless-add-group");

        try {
            // Okta Group Push wire shape: no "path", members nested under the value map.
            PatchRequest patchRequest = new PatchRequest();
            patchRequest.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));

            PatchRequestOperationsInner op = new PatchRequestOperationsInner();
            // Okta's Group Push uses op=replace (not add) for the path-less wire shape,
            // even when populating an empty group for the first time. The members list
            // inside `value` is the new authoritative set.
            op.setOp("replace");
            op.setValue(Map.of("members", List.of(Map.of("value", user.getId()))));

            patchRequest.setOperations(List.of(op));

            // Asking for "members" obliges the server to answer 200 with a
            // body carrying that attribute (RFC 7644 §3.5.2).
            Group patched = scimClient.patchGroup(group.getId(), patchRequest, "members");

            assertNotNull(patched);
            assertNotNull(patched.getMembers());
            assertEquals(1, patched.getMembers().size());
            assertEquals(user.getId(), patched.getMembers().get(0).getValue());

            // Verify via a fresh GET as well.
            Group after = scimClient.findGroup(group.getId());
            assertNotNull(after.getMembers());
            assertEquals(1, after.getMembers().size());
            assertEquals(user.getId(), after.getMembers().get(0).getValue());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Regression: removing a group member whose email is null must not 500.
     * Map.of() rejects null values, so the admin event dispatch previously
     * NPE'd when the user had no email set.
     */
    @Test
    void testRemoveGroupMemberWithoutEmail() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "no-email-user", "No", "Email");
        Group group = createGroup(scimClient, "no-email-group");

        UserRepresentation userRep = findRealmUser(TestConsts.TEST_REALM, user.getId());
        userRep.setEmail(null);
        getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(TestConsts.TEST_REALM)
            .users()
            .get(user.getId())
            .update(userRep);

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

        PatchRequest removeRequest = new PatchRequest();
        removeRequest.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        PatchRequestOperationsInner removeOperation = new PatchRequestOperationsInner();
        removeOperation.setOp("remove");
        removeOperation.setPath("members[value eq \"" + user.getId() + "\"]");
        removeRequest.setOperations(List.of(removeOperation));

        assertEquals(204, scimClient.patchGroup(group.getId(), removeRequest).status());

        Group after = scimClient.findGroup(group.getId());
        assertTrue(after.getMembers() == null || after.getMembers().isEmpty());

        deleteRealmUser(TestConsts.TEST_REALM, user.getId());
        deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
    }

    /**
     * A user created outside SCIM (no scim-managed role) is invisible to GET /Users,
     * so adding it to a group must be rejected exactly like an unknown id.
     */
    @Test
    void testAddUnmanagedUserIsRejected() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "unmanaged-add", "Unmanaged", "Add");
        Group group = createGroup(scimClient, "unmanaged-add-group");

        try {
            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(), membersPatch("add", "members", unmanagedUserId)));
            assertEquals(400, ex.getCode());
            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBody());
            assertEquals("Unknown group member: " + unmanagedUserId, body.get("detail").asText());

            assertTrue(getRealmGroupMemberIds(TestConsts.TEST_REALM, group.getId()).isEmpty());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Service-account users belong to clients and must never be put into groups through SCIM
     */
    @Test
    void testAddServiceAccountUserIsRejected() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        Group group = createGroup(scimClient, "service-account-add-group");

        try {
            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(), membersPatch("add", "members", SERVICE_ACCOUNT_USER_ID)));
            assertEquals(400, ex.getCode());

            assertTrue(getRealmGroupMemberIds(TestConsts.TEST_REALM, group.getId()).isEmpty());
        } finally {
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * REPLACE members only swaps the SCIM-managed members; a member added by an
     * administrator outside SCIM stays in the group and stays hidden from the response.
     */
    @Test
    void testReplaceMembersKeepsUnmanagedMember() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        User oldMember = createUser(scimClient, "replace-keep-old", "Replace", "Old");
        User newMember = createUser(scimClient, "replace-keep-new", "Replace", "New");
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "unmanaged-replace", "Unmanaged", "Replace");
        Group group = createGroup(scimClient, "replace-keep-group");

        try {
            seedGroupWithMember(scimClient, group, oldMember);
            addRealmUserToGroup(TestConsts.TEST_REALM, unmanagedUserId, group.getId());

            assertEquals(204, scimClient.patchGroup(group.getId(),
                    membersPatch("replace", "members", newMember.getId())).status());

            assertGroupHasOnlyMember(scimClient, group, newMember);
            assertEquals(Set.of(newMember.getId(), unmanagedUserId),
                    getRealmGroupMemberIds(TestConsts.TEST_REALM, group.getId()));
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, oldMember.getId());
            deleteRealmUser(TestConsts.TEST_REALM, newMember.getId());
            deleteRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Removing a member that SCIM does not manage is rejected like an unknown id,
     * and the member stays in the group.
     */
    @Test
    void testRemoveUnmanagedMemberIsRejected() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "unmanaged-remove", "Unmanaged", "Remove");
        Group group = createGroup(scimClient, "unmanaged-remove-group");

        try {
            addRealmUserToGroup(TestConsts.TEST_REALM, unmanagedUserId, group.getId());

            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(),
                            membersPatch("remove", "members[value eq \"" + unmanagedUserId + "\"]")));
            assertEquals(400, ex.getCode());

            assertEquals(Set.of(unmanagedUserId), getRealmGroupMemberIds(TestConsts.TEST_REALM, group.getId()));
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    // --- helpers shared by the atomic-resolution tests ---

    /**
     * A multi-operation PATCH whose later operation fails must not apply the
     * earlier operations (RFC 7644 §3.5.2): HTTP 400, the member added by the
     * first operation is not in the group, and no membership admin event is
     * recorded.
     */
    @Test
    void testMultiOperationPatchIsAtomicWhenLaterOperationFails() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User seeded = createUser(scimClient, "atomic-multi-seeded", "Atomic", "Seeded");
        User candidate = createUser(scimClient, "atomic-multi-candidate", "Atomic", "Candidate");
        Group group = createGroup(scimClient, "atomic-multi-group");

        try {
            seedGroupWithMember(scimClient, group, seeded);
            clearAdminEvents();

            GroupMembersInner candidateRef = new GroupMembersInner();
            candidateRef.setValue(candidate.getId());
            PatchRequestOperationsInner addOp = new PatchRequestOperationsInner();
            addOp.setOp("add");
            addOp.setPath("members");
            addOp.setValue(List.of(candidateRef));

            PatchRequestOperationsInner removeOp = new PatchRequestOperationsInner();
            removeOp.setOp("remove");
            removeOp.setPath("members[value eq \"33333333-3333-3333-3333-333333333333\"]");

            PatchRequest patch = new PatchRequest();
            patch.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
            patch.setOperations(List.of(addOp, removeOp));

            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(), patch));
            assertEquals(400, ex.getCode());

            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBody());
            assertEquals("invalidValue", body.get("scimType").asText());

            // The add from the first operation must have been rolled back.
            assertGroupHasOnlyMember(scimClient, group, seeded);

            // ...and so must its admin event.
            assertTrue(getAdminEventsIfAny().stream().noneMatch(event -> event.getResourceType() == ResourceType.GROUP_MEMBERSHIP),
                    "no membership admin event should be recorded for a rolled back PATCH");
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, candidate.getId());
            deleteRealmUser(TestConsts.TEST_REALM, seeded.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * An "add" operation without a value must fail the whole PATCH with
     * 400 invalidValue instead of silently skipping it and every operation
     * after it while answering 204.
     */
    @Test
    void testAddWithoutValueFailsWholePatch() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = createGroup(scimClient, "missing-value-group");

        try {
            PatchRequestOperationsInner addOp = new PatchRequestOperationsInner();
            addOp.setOp("add");
            addOp.setPath("members");

            PatchRequestOperationsInner renameOp = new PatchRequestOperationsInner();
            renameOp.setOp("replace");
            renameOp.setPath("displayName");
            renameOp.setValue("missing-value-group-renamed");

            PatchRequest patch = new PatchRequest();
            patch.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
            patch.setOperations(List.of(addOp, renameOp));

            ApiException ex = assertThrows(ApiException.class,
                    () -> scimClient.patchGroup(group.getId(), patch));
            assertEquals(400, ex.getCode());

            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBody());
            assertEquals("invalidValue", body.get("scimType").asText());

            Group after = scimClient.findGroup(group.getId());
            assertEquals("missing-value-group", after.getDisplayName());
        } finally {
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Returns the recorded admin events, or an empty list when the test event
     * listener has not written any since the last clear.
     */
    private List<AdminEvent> getAdminEventsIfAny() throws IOException {
        try {
            Container.ExecResult result = getKeycloakContainer().execInContainer(
                "sh", "-c", "ls /tmp/testdata/admin-events/*.json"
            );
            if (result.getExitCode() != 0) {
                return List.of();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while listing admin events", e);
        }

        return getAdminEvents();
    }

    /**
     * Seed a group with a single member via a path-based ADD members PatchOp.
     * Mirrors what an upstream IdP would push to establish initial membership
     * before the test exercises a subsequent REPLACE/REMOVE op.
     */
    private void seedGroupWithMember(ScimClient scimClient, Group group, User user) throws ApiException {
        PatchRequest seed = new PatchRequest();
        seed.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        PatchRequestOperationsInner addOp = new PatchRequestOperationsInner();
        addOp.setOp("add");
        addOp.setPath("members");
        GroupMembersInner known = new GroupMembersInner();
        known.setValue(user.getId());
        addOp.setValue(List.of(known));
        seed.setOperations(List.of(addOp));
        scimClient.patchGroup(group.getId(), seed);
    }

    /**
     * Builds a single-operation members PatchRequest
     *
     * @param op operation
     * @param path path
     * @param memberIds member IDs to send as the value, none for a filtered remove
     * @return patch request
     */
    private PatchRequest membersPatch(String op, String path, String... memberIds) {
        PatchRequest request = new PatchRequest();
        request.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        PatchRequestOperationsInner operation = new PatchRequestOperationsInner();
        operation.setOp(op);
        operation.setPath(path);
        if (memberIds.length > 0) {
            List<GroupMembersInner> refs = new java.util.ArrayList<>();
            for (String memberId : memberIds) {
                GroupMembersInner ref = new GroupMembersInner();
                ref.setValue(memberId);
                refs.add(ref);
            }
            operation.setValue(refs);
        }
        request.setOperations(List.of(operation));
        return request;
    }

    /**
     * Re-fetch the group and assert it has exactly one member, whose value
     * matches the given user's id. Used to confirm membership is unchanged
     * after a failed atomic PatchOp.
     */
    private void assertGroupHasOnlyMember(ScimClient scimClient, Group group, User user) throws ApiException {
        Group after = scimClient.findGroup(group.getId());
        assertNotNull(after.getMembers());
        assertEquals(1, after.getMembers().size());
        assertEquals(user.getId(), after.getMembers().get(0).getValue());
    }
}

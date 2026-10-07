package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.Group;
import fi.metatavu.keycloak.scim.server.test.client.model.GroupMembersInner;
import fi.metatavu.keycloak.scim.server.test.client.model.GroupsList;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.utils.ScimErrorAssertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that group responses cost a constant amount regardless of how many
 * members the group has.
 *
 * <p>Background: provisioning a group one member at a time used to re-serialise
 * the entire membership on every single request, so a directory sync pushing
 * 27k users into one group did O(n²) work and exhausted the heap. The defence
 * is RFC 7644 §3.5.2 (PATCH may answer 204 No Content) plus §3.9
 * (attributes / excludedAttributes), which is what these tests pin down.
 *
 * <p>The large-group test runs against {@value #DEFAULT_LARGE_GROUP_SIZE}
 * members by default to keep the suite quick. Set
 * {@code -Dscim.test.largeGroupSize=10000} to run it at the size that actually
 * caused the incident.
 */
@Testcontainers
class RealmGroupAttributeSelectionTestsIT extends AbstractInternalAuthRealmScimTest {

    private static final int DEFAULT_LARGE_GROUP_SIZE = 150;
    private static final String LARGE_GROUP_SIZE_PROPERTY = "scim.test.largeGroupSize";

    /**
     * A group read with excludedAttributes=members must omit the attribute
     * entirely, not serialise it as null. RFC 7643 §2.5 makes null, an empty
     * array and an absent attribute equivalent, so a null would claim the group
     * has no members rather than that the membership was not returned.
     */
    @Test
    void testFindGroupExcludingMembers() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "excluded-members-user", "Excluded", "User");
        Group group = createGroup(scimClient, "excluded-members-group");

        try {
            addMember(scimClient, group, user);

            ScimClient.ScimResponse response = scimClient.findGroupRaw(group.getId(), "excludedAttributes=members");

            assertEquals(200, response.status());
            assertFalse(response.hasAttribute("members"), "members must be absent, not null: " + response.body());

            // The minimum attribute set (RFC 7644 §3.9) survives exclusion.
            assertTrue(response.hasAttribute("id"));
            assertTrue(response.hasAttribute("schemas"));
            assertTrue(response.hasAttribute("meta"));
            assertTrue(response.hasAttribute("displayName"));
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * attributes= overrides the default attribute set entirely, so members is
     * dropped unless it is named.
     */
    @Test
    void testFindGroupWithExplicitAttributes() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "explicit-attributes-user", "Explicit", "User");
        Group group = createGroup(scimClient, "explicit-attributes-group");

        try {
            addMember(scimClient, group, user);

            Group displayNameOnly = scimClient.findGroup(group.getId(), "displayName", null);
            assertEquals("explicit-attributes-group", displayNameOnly.getDisplayName());
            assertNull(displayNameOnly.getMembers());
            assertNotNull(displayNameOnly.getId());

            Group membersOnly = scimClient.findGroup(group.getId(), "members", null);
            assertNull(membersOnly.getDisplayName());
            assertNotNull(membersOnly.getMembers());
            assertEquals(1, membersOnly.getMembers().size());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Without either parameter, members is still returned: it is
     * "returned": "default" in the core Group schema (RFC 7643 §8.7.1), and
     * the fix must not quietly change what a plain read means.
     */
    @Test
    void testFindGroupReturnsMembersByDefault() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "default-members-user", "Default", "User");
        Group group = createGroup(scimClient, "default-members-group");

        try {
            addMember(scimClient, group, user);

            Group found = scimClient.findGroup(group.getId());
            assertNotNull(found.getMembers());
            assertEquals(1, found.getMembers().size());
            assertEquals(user.getId(), found.getMembers().get(0).getValue());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * The group query endpoint honours excludedAttributes too. Microsoft Entra
     * ID issues {@code GET /Groups?excludedAttributes=members&filter=...}, and
     * ignoring it meant every group in the page dragged its whole membership
     * into the response.
     */
    @Test
    void testListGroupsExcludingMembers() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "list-excluded-user", "List", "User");
        Group group = createGroup(scimClient, "list-excluded-group");

        try {
            addMember(scimClient, group, user);

            GroupsList groups = scimClient.listGroups(null, 0, 10, null, "members");
            assertNotNull(groups.getResources());
            assertFalse(groups.getResources().isEmpty());
            for (Group listed : groups.getResources()) {
                assertNull(listed.getMembers(), "members must be excluded for " + listed.getDisplayName());
                assertNotNull(listed.getId());
            }

            ScimClient.ScimResponse raw = scimClient.listGroupsRaw("excludedAttributes=members");
            assertEquals(200, raw.status());
            assertFalse(raw.hasAttribute("members"), "members must be absent: " + raw.body());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * RFC 7644 §3.9 defines attributes and excludedAttributes as mutually
     * exclusive, so asking for both is a client error rather than a guess.
     */
    @Test
    void testConflictingAttributeParameters() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = createGroup(scimClient, "conflicting-attributes-group");

        try {
            ApiException exception = assertThrows(ApiException.class, () -> scimClient.findGroupRaw(
                    group.getId(),
                    "attributes=displayName&excludedAttributes=members"
            ));

            ScimErrorAssertions.assertScimError(exception, 400, "mutually exclusive");
        } finally {
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Supplying attributes on PATCH turns the 204 into a 200 carrying that
     * attribute — a MUST in RFC 7644 §3.5.2.
     */
    @Test
    void testPatchReturnsBodyWhenAttributesRequested() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "patch-attributes-user", "Patch", "User");
        Group group = createGroup(scimClient, "patch-attributes-group");

        try {
            Group patched = scimClient.patchGroup(group.getId(), addMemberRequest(user), "members");

            assertNotNull(patched);
            assertNotNull(patched.getMembers());
            assertEquals(1, patched.getMembers().size());
            assertEquals(user.getId(), patched.getMembers().get(0).getValue());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * The regression guard for the incident itself.
     *
     * <p>Adds members to a group one PATCH at a time — the shape Microsoft
     * Entra ID uses — and asserts that neither the PATCH response nor a
     * members-excluded read grows as the group fills up. Response size is the
     * assertion that matters: under the old behaviour the PATCH body was the
     * entire membership, so it grew linearly and the work behind it grew
     * quadratically over the run.
     *
     * <p>Latency is also checked, but only loosely. It exists to catch a
     * catastrophic reintroduction of per-request O(n) work, not to measure
     * performance on shared CI hardware.
     */
    @Test
    void testIncrementalMembershipProvisioningStaysConstant() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        int groupSize = Integer.getInteger(LARGE_GROUP_SIZE_PROPERTY, DEFAULT_LARGE_GROUP_SIZE);
        Group group = createGroup(scimClient, "large-provisioning-group");
        List<User> users = new ArrayList<>(groupSize);

        try {
            for (int i = 0; i < groupSize; i++) {
                users.add(createUser(scimClient, String.format("bulk-member-%d", i), "Bulk", "Member"));
            }

            // First member: the group is empty, so this is the cheapest possible case.
            long firstStarted = System.nanoTime();
            ScimClient.ScimResponse firstPatch = scimClient.patchGroup(group.getId(), addMemberRequest(users.get(0)));
            long firstDurationMs = (System.nanoTime() - firstStarted) / 1_000_000;

            assertEquals(204, firstPatch.status());
            assertEquals(0, firstPatch.size());

            int firstExcludedSize = scimClient.findGroupRaw(group.getId(), "excludedAttributes=members").size();

            for (int i = 1; i < groupSize - 1; i++) {
                assertEquals(204, scimClient.patchGroup(group.getId(), addMemberRequest(users.get(i))).status());
            }

            // Last member: the group now holds groupSize - 1 members. Adding one
            // more must cost the same as adding the first.
            long lastStarted = System.nanoTime();
            ScimClient.ScimResponse lastPatch = scimClient.patchGroup(
                    group.getId(),
                    addMemberRequest(users.get(groupSize - 1))
            );
            long lastDurationMs = (System.nanoTime() - lastStarted) / 1_000_000;

            assertEquals(204, lastPatch.status());
            assertEquals(0, lastPatch.size(), "PATCH response must not carry the member list");

            ScimClient.ScimResponse lastExcluded = scimClient.findGroupRaw(group.getId(), "excludedAttributes=members");
            assertEquals(200, lastExcluded.status());
            assertEquals(
                    firstExcludedSize,
                    lastExcluded.size(),
                    "a members-excluded read must not grow with the group"
            );

            // Confirm the group really did fill up, so the assertions above
            // were not satisfied by a no-op.
            Group full = scimClient.findGroup(group.getId());
            assertNotNull(full.getMembers());
            assertEquals(groupSize, full.getMembers().size());

            long latencyBudgetMs = Math.max(firstDurationMs * 10, 2000);
            assertTrue(
                    lastDurationMs <= latencyBudgetMs,
                    String.format(
                            "adding the %dth member took %d ms against a budget of %d ms (first member took %d ms)",
                            groupSize, lastDurationMs, latencyBudgetMs, firstDurationMs
                    )
            );
        } finally {
            for (User user : users) {
                deleteRealmUser(TestConsts.TEST_REALM, user.getId());
            }
            deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
        }
    }

    /**
     * Adds a user to a group over SCIM PATCH.
     *
     * @param scimClient SCIM client
     * @param group group
     * @param user user to add
     * @throws ApiException thrown when the call fails
     */
    private void addMember(ScimClient scimClient, Group group, User user) throws ApiException {
        assertEquals(204, scimClient.patchGroup(group.getId(), addMemberRequest(user)).status());
    }

    /**
     * Builds the PatchOp a directory sync sends to add a single member.
     *
     * @param user user to add
     * @return patch request
     */
    private PatchRequest addMemberRequest(User user) {
        PatchRequest patchRequest = new PatchRequest();
        patchRequest.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));

        PatchRequestOperationsInner operation = new PatchRequestOperationsInner();
        operation.setOp("add");
        operation.setPath("members");

        GroupMembersInner member = new GroupMembersInner();
        member.setValue(user.getId());
        operation.setValue(Collections.singletonList(member));

        patchRequest.setOperations(List.of(operation));

        return patchRequest;
    }
}

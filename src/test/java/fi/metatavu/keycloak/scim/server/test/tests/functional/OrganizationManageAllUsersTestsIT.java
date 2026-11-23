package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UsersList;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import fi.metatavu.keycloak.scim.server.test.utils.ScimErrorAssertions;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SCIM_MANAGE_ALL_USERS organization setting, which exposes organization members
 * that were not created through SCIM (users without the scim-managed role) to the SCIM API.
 * Such members are "unmanaged" in Keycloak terms: the organization does not own their account.
 */
@Testcontainers
class OrganizationManageAllUsersTestsIT extends AbstractOrganizationScimTest {

    private static final String MANAGE_ALL_USERS = "SCIM_MANAGE_ALL_USERS";

    @AfterEach
    void resetManageAllUsers() {
        setOrganizationAttribute(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, MANAGE_ALL_USERS, null);
    }

    @Test
    void testListUsersHidesUnmanagedMembersByDefault() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        User managed = createUser(scimClient, "managed-list", "Managed", "List");
        String unmanagedUserId = createUnmanagedMember("unmanaged-list");

        try {
            UsersList usersList = scimClient.listUsers(null, 0, 10);

            assertEquals(1, usersList.getTotalResults());
            assertNotNull(usersList.getResources());
            assertEquals(managed.getId(), usersList.getResources().getFirst().getId());
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, managed.getId());
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId);
        }
    }

    @Test
    void testListUsersIncludesUnmanagedMembersWhenEnabled() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        User managed = createUser(scimClient, "managed-list", "Managed", "List");
        String unmanagedUserId = createUnmanagedMember("unmanaged-list");

        try {
            setOrganizationAttribute(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, MANAGE_ALL_USERS, "true");

            UsersList usersList = scimClient.listUsers(null, 0, 10);

            assertEquals(2, usersList.getTotalResults());
            assertNotNull(usersList.getResources());
            assertTrue(usersList.getResources().stream().anyMatch(user -> managed.getId().equals(user.getId())));
            assertTrue(usersList.getResources().stream().anyMatch(user -> unmanagedUserId.equals(user.getId())));
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, managed.getId());
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId);
        }
    }

    @Test
    void testDeleteUnmanagedMemberIsForbiddenByDefault() {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        String unmanagedUserId = createUnmanagedMember("unmanaged-delete");

        try {
            ApiException exception = assertThrows(ApiException.class, () -> scimClient.deleteUser(unmanagedUserId));
            ScimErrorAssertions.assertScimError(exception, 403, "User is not managed by SCIM");

            assertNotNull(findRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId));
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId);
        }
    }

    @Test
    void testDeleteUnmanagedMemberWhenEnabled() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        String unmanagedUserId = createUnmanagedMember("unmanaged-delete-enabled");

        try {
            setOrganizationAttribute(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, MANAGE_ALL_USERS, "true");
            clearAdminEvents();

            scimClient.deleteUser(unmanagedUserId);

            // Keycloak only drops the membership of a member it does not own, so the realm account survives
            assertThrows(NotFoundException.class, () ->
                findOrganizationMember(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, unmanagedUserId)
            );
            assertNotNull(findRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId));

            // Only the membership removal is reported; no user delete event since the account still exists
            List<AdminEvent> adminEvents = getAdminEvents();
            assertEquals(1, adminEvents.size());
            assertOrganizationMemberEvent(
                adminEvents.getFirst(),
                TestConsts.ORGANIZATIONS_REALM,
                TestConsts.ORGANIZATIONS_REALM_ID,
                unmanagedUserId,
                TestConsts.ORGANIZATION_1_ID,
                OperationType.DELETE
            );
        } finally {
            deleteRealmUserIfExists(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId);
        }
    }

    @Test
    void testDeleteNonMemberIsNotFoundWhenEnabled() {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        String outsiderId = createRealmUser(TestConsts.ORGANIZATIONS_REALM, "outsider-delete", "Outsider", "Delete");

        try {
            setOrganizationAttribute(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, MANAGE_ALL_USERS, "true");

            // A realm user who is not a member of this organization stays out of reach
            ApiException exception = assertThrows(ApiException.class, () -> scimClient.deleteUser(outsiderId));
            assertEquals(404, exception.getCode());

            assertNotNull(findRealmUser(TestConsts.ORGANIZATIONS_REALM, outsiderId));
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, outsiderId);
        }
    }

    @Test
    void testUpdateUnmanagedMemberWhenEnabled() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        String unmanagedUserId = createUnmanagedMember("unmanaged-update");

        try {
            setOrganizationAttribute(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, MANAGE_ALL_USERS, "true");

            User found = scimClient.findUser(unmanagedUserId);
            assertEquals("unmanaged-update", found.getUserName());

            found.setName(getName("Updated", "Name"));
            User updated = scimClient.updateUser(unmanagedUserId, found);
            assertNotNull(updated.getName());
            assertEquals("Updated", updated.getName().getGivenName());

            UserRepresentation realmUser = findRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId);
            assertEquals("Updated", realmUser.getFirstName());
            assertEquals("Name", realmUser.getLastName());
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId);
        }
    }

    @Test
    void testUnmanagedMemberIsNotFoundByDefault() {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        String unmanagedUserId = createUnmanagedMember("unmanaged-by-id");

        try {
            assertUserNotFound(scimClient, unmanagedUserId);
            assertUserUnchanged(unmanagedUserId, "unmanaged-by-id");
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId);
        }
    }

    @Test
    void testMemberWithScimManagedRoleIsReachableByDefault() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        String memberId = createUnmanagedMember("role-member-by-id");

        try {
            // An existing member that was given the scim-managed role is in scope, as it is in GET /Users
            grantRealmRole(TestConsts.ORGANIZATIONS_REALM, memberId, "scim-managed");

            assertEquals("role-member-by-id", scimClient.findUser(memberId).getUserName());

            User patched = scimClient.patchUser(memberId, deactivateRequest());
            assertEquals(Boolean.FALSE, patched.getActive());
            assertFalse(findRealmUser(TestConsts.ORGANIZATIONS_REALM, memberId).isEnabled());
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, memberId);
        }
    }

    @Test
    void testUserOfAnotherOrganizationIsNotFound() throws ApiException {
        ScimClient organization1Client = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        ScimClient organization2Client = getAuthenticatedScimClient(TestConsts.ORGANIZATION_2_ID);
        User otherOrganizationUser = createUser(organization2Client, "other-org-by-id", "OtherOrg", "Member");

        try {
            assertUserNotFound(organization1Client, otherOrganizationUser.getId());
            assertUserUnchanged(otherOrganizationUser.getId(), "other-org-by-id");

            // Manage-all widens the scope to every member of organization 1, never to other organizations
            setOrganizationAttribute(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, MANAGE_ALL_USERS, "true");

            assertUserNotFound(organization1Client, otherOrganizationUser.getId());
            assertUserUnchanged(otherOrganizationUser.getId(), "other-org-by-id");
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, otherOrganizationUser.getId());
        }
    }

    @Test
    void testNonMemberIsNotFoundWhenEnabled() {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        String outsiderId = createRealmUser(TestConsts.ORGANIZATIONS_REALM, "outsider-by-id", "Outsider", "Member");

        try {
            setOrganizationAttribute(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, MANAGE_ALL_USERS, "true");

            assertUserNotFound(scimClient, outsiderId);
            assertUserUnchanged(outsiderId, "outsider-by-id");
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, outsiderId);
        }
    }

    @Test
    void testPatchUnmanagedMemberWhenEnabled() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        String unmanagedUserId = createUnmanagedMember("unmanaged-patch-enabled");

        try {
            setOrganizationAttribute(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, MANAGE_ALL_USERS, "true");

            User patched = scimClient.patchUser(unmanagedUserId, deactivateRequest());
            assertEquals(Boolean.FALSE, patched.getActive());
            assertFalse(findRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId).isEnabled());
        } finally {
            deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, unmanagedUserId);
        }
    }

    /**
     * Asserts that GET, PUT and PATCH by id all answer 404 for the given user
     *
     * @param scimClient SCIM client
     * @param userId user ID
     */
    private void assertUserNotFound(ScimClient scimClient, String userId) {
        ApiException findException = assertThrows(ApiException.class, () -> scimClient.findUser(userId));
        ScimErrorAssertions.assertScimError(findException, 404, "User not found");

        User updateRequest = createUserRequest("renamed-by-scim");
        updateRequest.setActive(false);
        updateRequest.setName(getName("Renamed", "BySCIM"));
        ApiException updateException = assertThrows(ApiException.class, () -> scimClient.updateUser(userId, updateRequest));
        ScimErrorAssertions.assertScimError(updateException, 404, "User not found");

        ApiException patchException = assertThrows(ApiException.class, () -> scimClient.patchUser(userId, deactivateRequest()));
        ScimErrorAssertions.assertScimError(patchException, 404, "User not found");
    }

    /**
     * Asserts that the user still has its original username and is still enabled
     *
     * @param userId user ID
     * @param username original username
     */
    private void assertUserUnchanged(String userId, String username) {
        UserRepresentation realmUser = findRealmUser(TestConsts.ORGANIZATIONS_REALM, userId);
        assertEquals(username, realmUser.getUsername());
        assertTrue(realmUser.isEnabled());
    }

    /**
     * Builds a PATCH request that sets active to false
     *
     * @return patch request
     */
    private PatchRequest deactivateRequest() {
        return new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(
                new PatchRequestOperationsInner()
                    .op("replace")
                    .path("active")
                    .value(Boolean.FALSE)
            ));
    }

    /**
     * Creates a member of organization 1 through the admin API, so it has no scim-managed role
     *
     * @param username username
     * @return created user ID
     */
    private String createUnmanagedMember(String username) {
        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(username);
        newUser.setFirstName("Unmanaged");
        newUser.setLastName("Member");
        newUser.setEmail(String.format("%s@example.com", username));
        newUser.setEnabled(true);
        return createOrganizationMember(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, newUser);
    }

}

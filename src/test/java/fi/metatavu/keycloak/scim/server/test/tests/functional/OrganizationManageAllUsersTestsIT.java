package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
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

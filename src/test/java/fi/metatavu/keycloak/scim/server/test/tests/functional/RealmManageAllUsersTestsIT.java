package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UsersList;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.utils.ScimErrorAssertions;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the scim.manage.all.users realm setting, which exposes users that were not
 * created through SCIM (users without the scim-managed role) to the SCIM API.
 */
@Testcontainers
class RealmManageAllUsersTestsIT extends AbstractInternalAuthRealmScimTest {

    private static final String MANAGE_ALL_USERS = "scim.manage.all.users";

    /** service-account-scim-client from kc-test.json, which has no scim-managed role */
    private static final String SERVICE_ACCOUNT_USER_ID = "7437c9e2-f7de-4bb1-b52f-6a6a61a598ab";

    @AfterEach
    void resetManageAllUsers() {
        setRealmAttribute(TestConsts.TEST_REALM, MANAGE_ALL_USERS, null);
    }

    @Test
    void testListUsersHidesUnmanagedUsersByDefault() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "unmanaged-list", "Unmanaged", "List");

        try {
            UsersList usersList = scimClient.listUsers(null, 0, 10);

            assertEquals(1, usersList.getTotalResults());
            assertNotNull(usersList.getResources());
            assertEquals(List.of("testadmin"), usersList.getResources().stream().map(User::getUserName).toList());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
        }
    }

    @Test
    void testListUsersIncludesUnmanagedUsersWhenEnabled() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "unmanaged-list", "Unmanaged", "List");

        try {
            setRealmAttribute(TestConsts.TEST_REALM, MANAGE_ALL_USERS, "true");

            UsersList usersList = scimClient.listUsers(null, 0, 10);

            assertEquals(2, usersList.getTotalResults());
            assertNotNull(usersList.getResources());
            assertEquals(2, usersList.getResources().size());
            assertTrue(usersList.getResources().stream().anyMatch(user -> unmanagedUserId.equals(user.getId())));
            assertTrue(usersList.getResources().stream().anyMatch(user -> "testadmin".equals(user.getUserName())));
            assertTrue(usersList.getResources().stream().noneMatch(user -> SERVICE_ACCOUNT_USER_ID.equals(user.getId())));
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
        }
    }

    @Test
    void testListUsersFilterAppliesToUnmanagedUsersWhenEnabled() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "unmanaged-filter", "Unmanaged", "Filter");

        try {
            setRealmAttribute(TestConsts.TEST_REALM, MANAGE_ALL_USERS, "true");

            UsersList usersList = scimClient.listUsers("userName eq \"unmanaged-filter\"", 0, 10);

            assertEquals(1, usersList.getTotalResults());
            assertNotNull(usersList.getResources());
            assertEquals(unmanagedUserId, usersList.getResources().getFirst().getId());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
        }
    }

    @Test
    void testDeleteUnmanagedUserIsForbiddenByDefault() {
        ScimClient scimClient = getAuthenticatedScimClient();
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "unmanaged-delete", "Unmanaged", "Delete");

        try {
            ApiException exception = assertThrows(ApiException.class, () -> scimClient.deleteUser(unmanagedUserId));
            ScimErrorAssertions.assertScimError(exception, 403, "User is not managed by SCIM");

            assertNotNull(findRealmUser(TestConsts.TEST_REALM, unmanagedUserId));
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
        }
    }

    @Test
    void testDeleteUnmanagedUserWhenEnabled() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "unmanaged-delete-enabled", "Unmanaged", "Delete");

        try {
            setRealmAttribute(TestConsts.TEST_REALM, MANAGE_ALL_USERS, "true");

            scimClient.deleteUser(unmanagedUserId);

            assertThrows(NotFoundException.class, () -> findRealmUser(TestConsts.TEST_REALM, unmanagedUserId));
        } finally {
            deleteRealmUserIfExists(TestConsts.TEST_REALM, unmanagedUserId);
        }
    }

    @Test
    void testDeleteServiceAccountIsForbiddenWhenEnabled() {
        ScimClient scimClient = getAuthenticatedScimClient();
        assertNotNull(findRealmUser(TestConsts.TEST_REALM, SERVICE_ACCOUNT_USER_ID));

        setRealmAttribute(TestConsts.TEST_REALM, MANAGE_ALL_USERS, "true");

        ApiException exception = assertThrows(ApiException.class, () -> scimClient.deleteUser(SERVICE_ACCOUNT_USER_ID));
        ScimErrorAssertions.assertScimError(exception, 403, "User is not managed by SCIM");

        assertNotNull(findRealmUser(TestConsts.TEST_REALM, SERVICE_ACCOUNT_USER_ID));
    }

    @Test
    void testUpdateUnmanagedUserWhenEnabled() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        String unmanagedUserId = createRealmUser(TestConsts.TEST_REALM, "unmanaged-update", "Unmanaged", "Update");

        try {
            setRealmAttribute(TestConsts.TEST_REALM, MANAGE_ALL_USERS, "true");

            User found = scimClient.findUser(unmanagedUserId);
            assertEquals("unmanaged-update", found.getUserName());

            found.setName(getName("Updated", "Name"));
            User updated = scimClient.updateUser(unmanagedUserId, found);
            assertNotNull(updated.getName());
            assertEquals("Updated", updated.getName().getGivenName());

            UserRepresentation realmUser = findRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
            assertEquals("Updated", realmUser.getFirstName());
            assertEquals("Name", realmUser.getLastName());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, unmanagedUserId);
        }
    }

}

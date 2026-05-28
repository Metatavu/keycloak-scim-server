package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for SCIM 2.0 User create endpoint
 */
@Testcontainers
public class RealmUserCreateTestsIT extends AbstractInternalAuthRealmScimTest {

    @Test
    void testCreateUser() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = new User();
        user.setUserName("new-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("New", "User"));
        user.setEmails(getEmails("new.user@example.com"));
        user.putAdditionalProperty("externalId", "my-external-id");
        user.putAdditionalProperty("preferredLanguage", "fi-FI");
        user.putAdditionalProperty("displayName", "The New User");
        user.putAdditionalProperty("job", "farmer");

        User created = scimClient.createUser(user);

        assertUser(created,
            created.getId(),
            "new-user",
            "New",
            "User",
            "new.user@example.com",
            "my-external-id",
            "fi-FI",
            "The New User"
        );
        assertEquals("farmer", created.getAdditionalProperty("job"));

        // Assert that the user was created in Keycloak
        UserRepresentation realmUser = findRealmUser(TestConsts.TEST_REALM, created.getId());
        assertNotNull(realmUser);
        assertEquals("new-user", realmUser.getUsername());
        assertEquals("New", realmUser.getFirstName());
        assertEquals("User", realmUser.getLastName());
        assertEquals("new.user@example.com", realmUser.getEmail());
        assertEquals(true, realmUser.isEnabled());
        assertEquals("my-external-id", realmUser.getAttributes().get("externalId").getFirst());
        assertEquals("fi-FI", realmUser.getAttributes().get("preferredLanguage").getFirst());
        assertEquals("The New User", realmUser.getAttributes().get("displayName").getFirst());
        assertEquals("farmer", realmUser.getAttributes().get("job").getFirst());

        // Assert that user has correct roles

        List<String> userRoles = getUserRealmRoleMappings(TestConsts.TEST_REALM, realmUser.getId()).stream()
            .map(RoleRepresentation::getName)
            .toList();

        assertArrayEquals(new String[] { "default-roles-test", "scim-managed" }, userRoles.toArray());

        // Assert that user has correct federated identity link
        FederatedIdentityRepresentation identityRepresentation = getUserFederatedIdentityLink(TestConsts.TEST_REALM, realmUser.getId()).getFirst();
        assertEquals(TestConsts.TEST_IDP, identityRepresentation.getIdentityProvider());
        assertEquals(realmUser.getAttributes().get("externalId").getFirst(), identityRepresentation.getUserId());
        assertEquals(realmUser.getUsername(), identityRepresentation.getUserName());

        // Clean up
        deleteRealmUser(TestConsts.TEST_REALM, realmUser.getId());
    }

    @Test
    void testCreateUserWithoutUsernameReturnsBadRequest() {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = new User();
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));


        try {
            scimClient.createUser(user);
            fail("Expected ApiException");
        } catch (ApiException e) {
            assertEquals(400, e.getCode());
        }
    }

    @Test
    void testCreateDuplicateUserReturnsConflict() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = new User();
        user.setUserName("dupe-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        // First creation should succeed
        User created = scimClient.createUser(user);
        assertNotNull(created);

        // Second creation should fail with 409 Conflict and identify the duplicated field
        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.createUser(user)
        );

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("username"),
            "Expected conflict message to mention 'username'; got: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("dupe-user"),
            "Expected conflict message to include the offending username; got: " + exception.getMessage());

        // Clean up
        deleteRealmUser(TestConsts.TEST_REALM, created.getId());
    }

    @Test
    void testCreateDuplicateEmailReturnsConflict() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User first = new User();
        first.setUserName("dupe-email-first");
        first.setActive(true);
        first.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        first.setEmails(getEmails("dupe.email@example.com"));

        User created = scimClient.createUser(first);
        assertNotNull(created);

        User second = new User();
        second.setUserName("dupe-email-second");
        second.setActive(true);
        second.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        second.setEmails(getEmails("dupe.email@example.com"));

        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.createUser(second)
        );

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("email"),
            "Expected conflict message to mention 'email'; got: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("dupe.email@example.com"),
            "Expected conflict message to include the offending email; got: " + exception.getMessage());

        deleteRealmUser(TestConsts.TEST_REALM, created.getId());
    }

    @Test
    void testCreateUserAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = new User();
        user.setUserName("new-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("New", "User"));
        user.setEmails(getEmails("new.user@example.com"));
        user.putAdditionalProperty("externalId", "my-external-id");
        user.putAdditionalProperty("preferredLanguage", "fi-FI");
        user.putAdditionalProperty("displayName", "The New User");
        user = scimClient.createUser(user);

        List<AdminEvent> adminEvents = getAdminEvents();
        assertEquals( 1, adminEvents.size());

        AdminEvent createUserEvent = adminEvents.stream()
            .filter(event -> event.getResourceType() == ResourceType.USER)
            .findFirst()
            .orElse(null);

        assertUserAdminEvent(
            createUserEvent,
            TestConsts.TEST_REALM,
            TestConsts.TEST_REALM_ID,
            user.getId(),
            OperationType.CREATE
        );
    }

}
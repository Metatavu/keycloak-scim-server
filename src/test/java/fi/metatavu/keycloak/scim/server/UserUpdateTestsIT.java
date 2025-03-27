package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User update (PUT) endpoint
 */
@Testcontainers
public class UserUpdateTestsIT extends AbstractScimTest {

    @Test
    void testReplaceUser() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create initial user
        User user = new User();
        user.setUserName("replace-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("Replace", "User"));
        user.setEmails(getEmails("replace.user@example.com"));

        User created = scimClient.createUser(user);
        assertNotNull(created);
        String userId = created.getId();

        // Replace user with updated data
        User replacement = new User();
        replacement.setUserName(user.getUserName());
        replacement.setActive(false);
        replacement.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        replacement.setName(getName("Replaced", "User"));
        replacement.setEmails(getEmails("replaced.user@example.com"));

        User updated = scimClient.updateUser(userId, replacement);

        assertNotNull(updated);
        assertNotNull(updated.getName());
        assertNotNull(updated.getEmails());
        assertNotNull(updated.getActive());
        assertEquals(userId, updated.getId());
        assertEquals("replace-user", updated.getUserName());
        assertEquals("Replaced", updated.getName().getGivenName());
        assertEquals("User", updated.getName().getFamilyName());
        assertEquals("replaced.user@example.com", updated.getEmails().getFirst().getValue());
        assertFalse(updated.getActive());

        // Also verify state in Keycloak
        UserRepresentation realmUser = findRealmUser(userId);
        assertNotNull(realmUser);
        assertEquals("replace-user", realmUser.getUsername());
        assertEquals("Replaced", realmUser.getFirstName());
        assertEquals("User", realmUser.getLastName());
        assertEquals("replaced.user@example.com", realmUser.getEmail());
        assertFalse(realmUser.isEnabled());

        // Clean up
        deleteRealmUser(userId);
    }

    @Test
    void testReplaceNonExistentUserReturnsNotFound() {
        ScimClient scimClient = getAuthenticatedScimClient();

        User replacement = new User();
        replacement.setUserName("ghost");
        replacement.setActive(true);
        replacement.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        ApiException exception = assertThrows(ApiException.class, () ->
                scimClient.updateUser("non-existent-id", replacement)
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void testReplaceUserWithConflictingUserNameReturnsConflict() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create two users
        User userA = new User();
        userA.setUserName("conflict-a");
        userA.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        User createdA = scimClient.createUser(userA);

        User userB = new User();
        userB.setUserName("conflict-b");
        userB.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        User createdB = scimClient.createUser(userB);

        // Try renaming B to A's username
        User replacement = new User();
        replacement.setUserName("conflict-a");
        replacement.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        ApiException exception = assertThrows(ApiException.class, () ->
                scimClient.updateUser(createdB.getId(), replacement)
        );

        assertEquals(409, exception.getCode());

        // Clean up
        deleteRealmUser(createdA.getId());
        deleteRealmUser(createdB.getId());
    }

}
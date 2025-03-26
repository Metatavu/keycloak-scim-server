package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UserEmailsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.UserName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User create endpoint
 */
@Testcontainers
public class UserCreateTestsIT extends AbstractScimTest {

    @Test
    void testCreateUser() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = new User();
        user.setUserName("new-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("New", "User"));
        user.setEmails(getEmails("new.user@example.com"));

        User created = scimClient.createUser(user);

        assertUser(created, created.getId(), "new-user", "New", "User", "new.user@example.com");

        // Assert that the user was created in Keycloak
        UserRepresentation realmUser = findRealmUser(created.getId());
        assertNotNull(realmUser);
        assertEquals("new-user", realmUser.getUsername());
        assertEquals("New", realmUser.getFirstName());
        assertEquals("User", realmUser.getLastName());
        assertEquals("new.user@example.com", realmUser.getEmail());
        assertEquals(true, realmUser.isEnabled());

        // Assert that user has correct roles

        List<String> userRoles = getUserRealmRoleMappings(realmUser.getId()).stream()
            .map(RoleRepresentation::getName)
            .toList();

        assertArrayEquals(new String[] { "default-roles-test", "scim-managed" }, userRoles.toArray());

        // Clean up
        deleteRealmUser(realmUser.getId());
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

        // Second creation should fail with 409 Conflict
        ApiException exception = assertThrows(ApiException.class, () ->
                scimClient.createUser(user)
        );

        assertEquals(409, exception.getCode());

        // Clean up
        deleteRealmUser(created.getId());
    }

    /**
     * Creates a UserName object for SCIM user
     *
     * @param givenName given name
     * @param familyName family name
     * @return UserName object
     */
    @SuppressWarnings("SameParameterValue")
    private UserName getName(
            String givenName,
            String familyName
    ) {
        UserName result = new UserName();
        result.setGivenName(givenName);
        result.setFamilyName(familyName);
        return result;
    }

    /**
     * Returns a list of email addresses for SCIM user
     *
     * @param value email address
     * @return list of email addresses
     */
    @SuppressWarnings("SameParameterValue")
    private List<UserEmailsInner> getEmails(String value) {
        UserEmailsInner result = new UserEmailsInner();
        result.setValue(value);
        return Collections.singletonList(result);
    }

}
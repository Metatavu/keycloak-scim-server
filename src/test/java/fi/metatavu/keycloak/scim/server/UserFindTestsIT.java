package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 user find (GET /Users/{id}) endpoint
 */
@Testcontainers
public class UserFindTestsIT extends AbstractScimTest {

    @Test
    void testFindUserById() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create user
        User user = new User();
        user.setUserName("find-me");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("Find", "Me"));
        user.setEmails(getEmails("find.me@example.com"));

        User created = scimClient.createUser(user);
        assertNotNull(created);
        String userId = created.getId();

        // Find the user
        User found = scimClient.findUser(userId);
        assertNotNull(found);
        assertEquals(userId, found.getId());
        assertEquals("find-me", found.getUserName());
        assertNotNull(found.getName());
        assertEquals("Find", found.getName().getGivenName());
        assertEquals("Me", found.getName().getFamilyName());
        assertNotNull(found.getEmails());
        assertEquals("find.me@example.com", found.getEmails().getFirst().getValue());

        // Clean up
        deleteRealmUser(userId);
    }

    @Test
    void testFindUserNotFound() {
        ScimClient scimClient = getAuthenticatedScimClient();

        String fakeId = "non-existent-id";

        ApiException exception = assertThrows(ApiException.class, () ->
                scimClient.findUser(fakeId)
        );

        assertEquals(404, exception.getCode());
    }

}
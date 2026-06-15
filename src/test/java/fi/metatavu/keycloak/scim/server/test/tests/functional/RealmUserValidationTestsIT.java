package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractValidationRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.utils.ScimErrorAssertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Validation tests for the realm SCIM user endpoint that require editUsernameAllowed=true.
 */
@Testcontainers
public class RealmUserValidationTestsIT extends AbstractValidationRealmScimTest {

    @Test
    void testCreateUserWithIncorrectUsernameReturnsBadRequest() {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUserRequest("invalid username");

        try {
            scimClient.createUser(user);
            fail("Expected ApiException");
        } catch (ApiException e) {
            assertEquals(400, e.getCode());
        }
    }

    @Test
    void testCreateUserWithIncorrectUsernameAndEmailReturnsBadRequest() {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUserRequest("invalid username", "invalid-email@");

        try {
            scimClient.createUser(user);
            fail("Expected ApiException");
        } catch (ApiException e) {
            ScimErrorAssertions.assertScimError(e, 400, "Validation failed: email: error-invalid-email; userName: error-username-invalid-character");
        }
    }

    @Test
    void testUpdateUserWithIncorrectUsernameReturnsBadRequest() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = new User();
        user.setUserName("update-invalid-username");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        User created = scimClient.createUser(user);
        assertNotNull(created);
        created.setUserName("update invalid username");

        try {
            scimClient.updateUser(created.getId(), created);
            fail("Expected ApiException");
        } catch (ApiException e) {
            assertEquals(400, e.getCode());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, created.getId());
        }
    }

}

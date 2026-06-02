package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.utils.ScimErrorAssertions;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.representations.idm.MemberRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User create endpoint
 */
@Testcontainers
public class OrganizationUserCreateTestsIT extends AbstractOrganizationScimTest {

    @Test
    void testCreateUser() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        User user = new User();
        user.setUserName("new-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("New", "User"));
        user.setEmails(getEmails("new.user@example.com"));
        user.putAdditionalProperty("externalId", "my-external-id");
        user.putAdditionalProperty("preferredLanguage", "fi-FI");
        user.putAdditionalProperty("displayName", "The New User");

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

        // Assert that the user was created in Keycloak
        UserRepresentation realmUser = findRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
        assertNotNull(realmUser);
        assertEquals("new-user", realmUser.getUsername());
        assertEquals("New", realmUser.getFirstName());
        assertEquals("User", realmUser.getLastName());
        assertEquals("new.user@example.com", realmUser.getEmail());
        assertEquals(true, realmUser.isEnabled());
        assertEquals("my-external-id", realmUser.getAttributes().get("externalId").getFirst());
        assertEquals("fi-FI", realmUser.getAttributes().get("preferredLanguage").getFirst());
        assertEquals("The New User", realmUser.getAttributes().get("displayName").getFirst());

        // Assert that user has correct roles

        List<String> userRoles = getUserRealmRoleMappings(TestConsts.ORGANIZATIONS_REALM, realmUser.getId()).stream()
            .map(RoleRepresentation::getName)
            .toList();

        assertArrayEquals(new String[] { "default-roles-organizations", "scim-managed" }, userRoles.toArray());

        // Assert that user belongs to organization 1 but not to organization 2

        MemberRepresentation organization1Member = findOrganizationMember(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, realmUser.getId());
        assertNotNull(organization1Member);
        // assertEquals(TestConsts.ORGANIZATION_1_ID, organization1Member.getOrganizationId());

        assertThrows(
            NotFoundException.class,
            () -> findOrganizationMember(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_2_ID, realmUser.getId())
        );

        // Clean up
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, realmUser.getId());
    }

    @Test
    void testCreateUserWithoutUsernameReturnsBadRequest() {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

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
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

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
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    @Test
    void testCreateDuplicateEmailReturnsConflict() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        User first = new User();
        first.setUserName("org-dupe-email-first");
        first.setActive(true);
        first.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        first.setEmails(getEmails("org.dupe.email@example.com"));

        User created = scimClient.createUser(first);
        assertNotNull(created);

        User second = new User();
        second.setUserName("org-dupe-email-second");
        second.setActive(true);
        second.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        second.setEmails(getEmails("org.dupe.email@example.com"));

        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.createUser(second)
        );

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("email"),
            "Expected conflict message to mention 'email'; got: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("org.dupe.email@example.com"),
            "Expected conflict message to include the offending email; got: " + exception.getMessage());

        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    @Test
    void testCreateEmailAsUsername() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_ID);

        User user = new User();
        user.setUserName("new.user@example.com");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("New", "User"));
        user.putAdditionalProperty("externalId", "my-external-id");
        user.putAdditionalProperty("preferredLanguage", "fi-FI");
        user.putAdditionalProperty("displayName", "The New User");

        User created = scimClient.createUser(user);

        assertUser(created,
                created.getId(),
                "new.user@example.com",
                "New",
                "User",
                "new.user@example.com",
                "my-external-id",
                "fi-FI",
                "The New User"
        );

        // Clean up
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    @Test
    void testCreateEmailAsUsernameMalformed() {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_ID);

        User user = new User();
        user.setUserName("new.user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("New", "User"));
        user.setEmails(getEmails("new.user@example.com"));
        user.putAdditionalProperty("externalId", "my-external-id");
        user.putAdditionalProperty("preferredLanguage", "fi-FI");
        user.putAdditionalProperty("displayName", "The New User");

        assertThrows(ApiException.class, () -> scimClient.createUser(user), "Invalid email format for userName");
    }

    @Test
    void testCreateUserAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

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
        assertEquals( 2, adminEvents.size());

        AdminEvent createUserEvent = adminEvents.stream()
                .filter(event -> event.getResourceType() == ResourceType.USER)
                .findFirst()
                .orElse(null);

        assertUserAdminEvent(
                createUserEvent,
                TestConsts.ORGANIZATIONS_REALM,
                TestConsts.ORGANIZATIONS_REALM_ID,
                user.getId(),
                OperationType.CREATE
        );

        AdminEvent addMemberEvent = adminEvents.stream()
                .filter(event -> event.getResourceType() == ResourceType.ORGANIZATION_MEMBERSHIP)
                .findFirst()
                .orElse(null);

        assertOrganizationMemberEvent(
                addMemberEvent,
                TestConsts.ORGANIZATIONS_REALM,
                TestConsts.ORGANIZATIONS_REALM_ID,
                user.getId(),
                TestConsts.ORGANIZATION_1_ID,
                OperationType.CREATE
        );
    }

        @Test
        void testCreateUserWithIncorrectUsernameReturnsBadRequest() {
            ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

            User user = new User();
            user.setActive(true);
            user.setUserName("invalid username");
            user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

            try {
                scimClient.createUser(user);
                fail("Expected ApiException");
            } catch (ApiException e) {
                assertEquals(400, e.getCode());
            }
        }

        @Test
        void testCreateUserWithIncorrectUsernameAndEmailReturnsBadRequest() {
            ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

            User user = new User();
            user.setActive(true);
            user.setUserName("invalid username");
            user.setEmails(getEmails("invalid-email@"));
            user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

            try {
                scimClient.createUser(user);
                fail("Expected ApiException");
            } catch (ApiException e) {
                ScimErrorAssertions.assertScimError(e, 400, "Validation failed: email: error-invalid-email; email: error-invalid-email; username: error-username-invalid-character");
            }
        }
}
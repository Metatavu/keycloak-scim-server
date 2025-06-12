package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User update (PUT) endpoint
 */
@Testcontainers
public class OrganizationUserUpdateTestsIT extends AbstractOrganizationScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
            .withNetwork(network)
            .withNetworkAliases("scim-keycloak")
            .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
            .withRealmImportFiles("kc-organizations.json", "kc-external.json")
            .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @Test
    void testReplaceUser() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        // Create initial user
        User user = new User();
        user.setUserName("replace-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("Replace", "User"));
        user.setEmails(getEmails("replace.user@example.com"));
        user.putAdditionalProperty("externalId", "replace-external-id");
        user.putAdditionalProperty("preferredLanguage", "en_US");
        user.putAdditionalProperty("displayName", "Replace User");
        
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
        replacement.putAdditionalProperty("displayName", "Replaced User");
        replacement.putAdditionalProperty("externalId", "replaced-external-id");
        replacement.putAdditionalProperty("preferredLanguage", "fi_FI");

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
        assertEquals("Replaced User", updated.getAdditionalProperty("displayName"));
        assertEquals("replaced-external-id", updated.getAdditionalProperty("externalId"));
        assertEquals("fi_FI", updated.getAdditionalProperty("preferredLanguage"));
        assertFalse(updated.getActive());

        // Also verify state in Keycloak
        UserRepresentation realmUser = findRealmUser(TestConsts.ORGANIZATIONS_REALM, userId);
        assertNotNull(realmUser);
        assertEquals("replace-user", realmUser.getUsername());
        assertEquals("Replaced", realmUser.getFirstName());
        assertEquals("User", realmUser.getLastName());
        assertEquals("replaced.user@example.com", realmUser.getEmail());
        assertEquals("Replaced User", realmUser.getAttributes().get("displayName").getFirst());
        assertEquals("replaced-external-id", realmUser.getAttributes().get("externalId").getFirst());
        assertEquals("fi_FI", realmUser.getAttributes().get("preferredLanguage").getFirst());
        assertFalse(realmUser.isEnabled());

        // Clean up
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, userId);
    }

    @Test
    void testReplaceNonExistentUserReturnsNotFound() {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

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
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

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
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, createdA.getId());
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, createdB.getId());
    }

    @Test
    void testUpdateUserWithEmailAsUsername() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_ID);

        // Test that updating username updates email, but leaves username unchanged

        User user = scimClient.findUser(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_EXISTING_USER_ID);
        user.setUserName("new-email@example.com");
        user.setEmails(null);
        User updatedUser = scimClient.updateUser(user.getId(), user);

        assertNotNull(updatedUser);
        assertEquals("new-email@example.com", updatedUser.getUserName());
        assertNotNull(updatedUser.getEmails());
        assertEquals(1, updatedUser.getEmails().size());
        assertEquals("new-email@example.com", updatedUser.getEmails().getFirst().getValue());

        User found = scimClient.findUser(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_EXISTING_USER_ID);
        assertNotNull(found);
        assertEquals("new-email@example.com", found.getUserName());
        assertNotNull(found.getEmails());
        assertEquals(1, found.getEmails().size());
        assertEquals("new-email@example.com", found.getEmails().getFirst().getValue());

        // Assert that the userName in Keycloak user has not been changed, but email is updated

        UserRepresentation realmUser = getKeycloakContainer().getKeycloakAdminClient()
                .realm(TestConsts.ORGANIZATIONS_REALM)
                .users()
                .get(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_EXISTING_USER_ID)
                .toRepresentation();

        assertEquals("existing-user", realmUser.getUsername());
        assertNotNull(realmUser.getEmail());
        assertEquals("new-email@example.com", realmUser.getEmail());

        // Revert changes

        user.setUserName("existing-user@example.com");
        scimClient.updateUser(user.getId(), user);
    }

    @Test
    void testUpdateUserAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        // Create user
        User user = new User();
        user.setUserName("patch-admin-events-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        User created = scimClient.createUser(user);
        clearAdminEvents();

        // Update user
        scimClient.updateUser(created.getId(), user);

        List<AdminEvent> adminEvents = getAdminEvents();
        assertEquals(1, adminEvents.size());

        AdminEvent updateUserEvent = adminEvents.getFirst();

        assertUserAdminEvent(
            updateUserEvent,
            TestConsts.ORGANIZATIONS_REALM,
            TestConsts.ORGANIZATIONS_REALM_ID,
            created.getId(),
            OperationType.UPDATE
        );

        // Cleanup
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

}
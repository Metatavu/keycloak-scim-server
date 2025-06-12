package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractRealmScimTest;
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
public class RealmUserUpdateTestsIT extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
        .withNetwork(network)
        .withNetworkAliases("scim-keycloak")
        .withEnv("SCIM_AUTHENTICATION_MODE", "KEYCLOAK")
        .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
        .withFileSystemBind(KeycloakTestUtils.getTestDataDir().getAbsolutePath(), "/tmp/testdata", BindMode.READ_WRITE)
        .withRealmImportFile("kc-test.json")
        .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

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
        UserRepresentation realmUser = findRealmUser(TestConsts.TEST_REALM, userId);
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
        deleteRealmUser(TestConsts.TEST_REALM, userId);
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
        deleteRealmUser(TestConsts.TEST_REALM, createdA.getId());
        deleteRealmUser(TestConsts.TEST_REALM, createdB.getId());
    }

    @Test
    void testUpdateUserAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

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
                TestConsts.TEST_REALM,
                TestConsts.TEST_REALM_ID,
                created.getId(),
                OperationType.UPDATE
        );

        // Cleanup
        deleteRealmUser(TestConsts.TEST_REALM, created.getId());
    }
}
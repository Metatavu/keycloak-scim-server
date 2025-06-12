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
import org.keycloak.events.admin.ResourceType;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User create endpoint
 */
@Testcontainers
public class RealmUserCreateTestsIT extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
        .withNetwork(network)
        .withNetworkAliases("scim-keycloak")
        .withEnv("SCIM_AUTHENTICATION_MODE", "KEYCLOAK")
        .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
        .withRealmImportFile("kc-test.json")
        .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

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

        // Assert that user has correct roles

        List<String> userRoles = getUserRealmRoleMappings(TestConsts.TEST_REALM, realmUser.getId()).stream()
            .map(RoleRepresentation::getName)
            .toList();

        assertArrayEquals(new String[] { "default-roles-test", "scim-managed" }, userRoles.toArray());

        // Clean up
        deleteRealmUser(TestConsts.TEST_REALM, realmUser.getId());
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
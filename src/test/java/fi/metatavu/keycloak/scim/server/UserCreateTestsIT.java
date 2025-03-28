package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User create endpoint
 */
@Testcontainers
public class UserCreateTestsIT extends AbstractScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
        .withNetwork(network)
        .withNetworkAliases("scim-keycloak")
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

}
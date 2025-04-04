package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.MemberRepresentation;
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
public class OrganizationUserCreateTestsIT extends AbstractOrganizationScimTest {

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
    void testCreateDuplicateUserReturnsConflict() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

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
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }
}
package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 user find (GET /Users/{id}) endpoint
 */
@Testcontainers
public class OrganizationUserFindTestsIT extends AbstractOrganizationScimTest {

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
    void testFindUserById() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

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
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, userId);
    }

    @Test
    void testFindUserNotFound() {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        String fakeId = "non-existent-id";

        ApiException exception = assertThrows(ApiException.class, () ->
                scimClient.findUser(fakeId)
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void testFindEmailAsUsername() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_ID);

        User existingUser = scimClient.findUser(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_EXISTING_USER_ID);
        assertNotNull(existingUser);
        assertEquals("existing-user@example.com", existingUser.getUserName());
    }

}
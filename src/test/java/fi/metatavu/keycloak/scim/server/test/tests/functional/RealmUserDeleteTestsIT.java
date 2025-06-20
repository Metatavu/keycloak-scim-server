package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User delete endpoint
 */
@Testcontainers
public class RealmUserDeleteTestsIT extends AbstractRealmScimTest {

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
    void testDeleteUser() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create user
        User user = new User();
        user.setUserName("delete-me");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("Delete", "Me"));
        user.setEmails(getEmails("delete.me@example.com"));

        User created = scimClient.createUser(user);
        assertNotNull(created);

        // Delete user
        scimClient.deleteUser(created.getId());

        // Try to fetch the user to confirm deletion
        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.findUser(created.getId())
        );

        assertEquals(404, exception.getCode());

        NotFoundException notFoundException = assertThrows(NotFoundException.class, () ->
            findRealmUser(TestConsts.TEST_REALM, created.getId())
        );
    }

    @Test
    void testDeleteNonexistentUserReturns404() {
        ScimClient scimClient = getAuthenticatedScimClient();

        String nonexistentId = "nonexistent-user-id";

        ApiException exception = assertThrows(ApiException.class, () ->
                scimClient.deleteUser(nonexistentId)
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void testDeleteUserAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createUser(scimClient, "delete-me", "Delete", "Me");
        clearAdminEvents();

        // Delete user
        scimClient.deleteUser(user.getId());

        List<AdminEvent> adminEvents = getAdminEvents();
        assertEquals(1, adminEvents.size());

        AdminEvent deleteUserEvent = adminEvents.stream()
            .filter(event -> event.getResourceType() == ResourceType.USER)
            .findFirst()
            .orElse(null);

        assertUserAdminEvent(
            deleteUserEvent,
            TestConsts.TEST_REALM,
            TestConsts.TEST_REALM_ID,
            user.getId(),
            OperationType.DELETE
        );
    }

}
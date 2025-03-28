package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User create endpoint
 */
@Testcontainers
public class UserPatchTestsIT extends AbstractScimTest {

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
    void testActivateAndDeactivateUser() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create an active user
        User user = new User();
        user.setUserName("patch-activation-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        User created = scimClient.createUser(user);
        assertNotNull(created);
        assertNotNull(created.getActive());
        assertTrue(created.getActive());

        UserRepresentation createdRealmUser = findRealmUser(created.getId());
        assertNotNull(createdRealmUser);
        assertTrue(createdRealmUser.isEnabled());

        // Deactivate user
        User deactivated = scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(new PatchRequestOperationsInner()
                    .op(PatchRequestOperationsInner.OpEnum.REPLACE)
                    .path("active")
                    .value(Boolean.FALSE)
            )));

        assertNotNull(deactivated);
        assertNotNull(deactivated.getActive());
        assertFalse(deactivated.getActive());

        UserRepresentation deactivatedRealmUser = findRealmUser(created.getId());
        assertNotNull(deactivatedRealmUser);
        assertFalse(deactivatedRealmUser.isEnabled());

        // Activate user
        User activated = scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(new PatchRequestOperationsInner()
                    .op(PatchRequestOperationsInner.OpEnum.REPLACE)
                    .path("active")
                    .value(Boolean.TRUE)
            )));

        assertNotNull(activated);
        assertNotNull(activated.getActive());
        assertTrue(activated.getActive());

        UserRepresentation activatedRealmUser = findRealmUser(created.getId());
        assertNotNull(activatedRealmUser);
        assertTrue(activatedRealmUser.isEnabled());

        // Cleanup
        deleteRealmUser(created.getId());
    }

}
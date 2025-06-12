package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
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
 * Tests for SCIM 2.0 User create endpoint
 */
@Testcontainers
public class OrganizationUserPatchTestsIT extends AbstractOrganizationScimTest {

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
    void testActivateAndDeactivateUser() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        // Create an active user
        User user = new User();
        user.setUserName("patch-activation-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        User created = scimClient.createUser(user);
        assertNotNull(created);
        assertNotNull(created.getActive());
        assertTrue(created.getActive());

        UserRepresentation createdRealmUser = findRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
        assertNotNull(createdRealmUser);
        assertTrue(createdRealmUser.isEnabled());

        // Deactivate user
        User deactivated = scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(
                new PatchRequestOperationsInner()
                    .op("Replace")
                    .path("active")
                    .value(Boolean.FALSE)
            )));

        assertNotNull(deactivated);
        assertNotNull(deactivated.getActive());
        assertFalse(deactivated.getActive());

        UserRepresentation deactivatedRealmUser = findRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
        assertNotNull(deactivatedRealmUser);
        assertFalse(deactivatedRealmUser.isEnabled());

        // Activate user
        User activated = scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(new PatchRequestOperationsInner()
                    .op("Replace")
                    .path("active")
                    .value(Boolean.TRUE)
            )));

        assertNotNull(activated);
        assertNotNull(activated.getActive());
        assertTrue(activated.getActive());

        UserRepresentation activatedRealmUser = findRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
        assertNotNull(activatedRealmUser);
        assertTrue(activatedRealmUser.isEnabled());

        // Cleanup
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    @Test
    void testPatchAttributes() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        // Create user
        User user = new User();
        user.setUserName("patch-attributes-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        User created = scimClient.createUser(user);
        assertNotNull(created);
        assertNull(created.getAdditionalProperty("externalId"));
        assertNull(created.getAdditionalProperty("displayName"));
        assertNull(created.getAdditionalProperty("preferredLanguage"));

        // Patch externalId, displayName, preferredLanguage
        User patched = scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(
                new PatchRequestOperationsInner()
                    .op("add")
                    .path("externalId")
                    .value("external-1234"),
                new PatchRequestOperationsInner()
                    .op("add")
                    .path("displayName")
                    .value("Display Name"),
                new PatchRequestOperationsInner()
                    .op("add")
                    .path("preferredLanguage")
                    .value("fi_FI")
            ))
        );

        assertNotNull(patched);
        assertEquals("external-1234", patched.getAdditionalProperty("externalId"));
        assertEquals("Display Name", patched.getAdditionalProperty("displayName"));
        assertEquals("fi_FI", patched.getAdditionalProperty("preferredLanguage"));

        // Re-Patch (replace)
        User patchedAgain = scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(
                new PatchRequestOperationsInner()
                    .op("replace")
                    .path("externalId")
                    .value("external-5678"),
                new PatchRequestOperationsInner()
                    .op("replace")
                    .path("displayName")
                    .value("Updated Display"),
                new PatchRequestOperationsInner()
                    .op("replace")
                    .path("preferredLanguage")
                    .value("en_US")
            ))
        );

        assertEquals("external-5678", patchedAgain.getAdditionalProperty("externalId"));
        assertEquals("Updated Display", patchedAgain.getAdditionalProperty("displayName"));
        assertEquals("en_US", patchedAgain.getAdditionalProperty("preferredLanguage"));

        // Cleanup
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    @Test
    void testPatchUsernameEmailAsUsername() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_ID);

        // Test that patching username with email as username enabled updates email instead of username

        User patched = scimClient.patchUser(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_EXISTING_USER_ID, new PatchRequest()
                .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
                .operations(List.of(
                        new PatchRequestOperationsInner()
                                .op("replace")
                                .path("userName")
                                .value("new-email@example.com")
                ))
        );

        assertNotNull(patched);
        assertEquals("new-email@example.com", patched.getUserName());
        assertNotNull(patched.getEmails());
        assertEquals(1, patched.getEmails().size());
        assertEquals("new-email@example.com", patched.getEmails().getFirst().getValue());

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

        scimClient.patchUser(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_EXISTING_USER_ID, new PatchRequest()
                .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
                .operations(List.of(
                        new PatchRequestOperationsInner()
                                .op("replace")
                                .path("userName")
                                .value("existing-user@example.com")
                ))
        );
    }

    @Test
    void testPatchUserAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        // Create user
        User user = new User();
        user.setUserName("patch-admin-events-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        User created = scimClient.createUser(user);
        clearAdminEvents();

        // Patch user
        scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(
                new PatchRequestOperationsInner()
                    .op("replace")
                    .path("userName")
                    .value("patched-user-name")
            )));

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
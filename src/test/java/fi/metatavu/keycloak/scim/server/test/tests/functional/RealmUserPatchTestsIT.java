package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SCIM 2.0 User create endpoint
 */
@Testcontainers
public class RealmUserPatchTestsIT extends AbstractInternalAuthRealmScimTest {

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

        UserRepresentation createdRealmUser = findRealmUser(TestConsts.TEST_REALM, created.getId());
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

        UserRepresentation deactivatedRealmUser = findRealmUser(TestConsts.TEST_REALM, created.getId());
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

        UserRepresentation activatedRealmUser = findRealmUser(TestConsts.TEST_REALM, created.getId());
        assertNotNull(activatedRealmUser);
        assertTrue(activatedRealmUser.isEnabled());

        // Cleanup
        deleteRealmUser(TestConsts.TEST_REALM, created.getId());
    }

    @Test
    void testPatchAttributes() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

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
        assertNull(created.getAdditionalProperty("job"));

        // Patch externalId, displayName, preferredLanguage from user profile and job from user attribute mapper
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
                    .value("en_US"),
                new PatchRequestOperationsInner()
                        .op("replace")
                        .path("job")
                        .value("pilot")
            ))
        );

        assertEquals("external-5678", patchedAgain.getAdditionalProperty("externalId"));
        assertEquals("Updated Display", patchedAgain.getAdditionalProperty("displayName"));
        assertEquals("en_US", patchedAgain.getAdditionalProperty("preferredLanguage"));
        assertEquals("pilot", patchedAgain.getAdditionalProperty("job"));

        // Also verify state in Keycloak
        UserRepresentation realmUser = findRealmUser(TestConsts.TEST_REALM, created.getId());
        assertNotNull(realmUser);
        assertEquals("external-5678", realmUser.getAttributes().get("externalId").getFirst());
        assertEquals("Updated Display", realmUser.getAttributes().get("displayName").getFirst());
        assertEquals("en_US", realmUser.getAttributes().get("preferredLanguage").getFirst());
        assertEquals("pilot", realmUser.getAttributes().get("job").getFirst());

        // Cleanup
        deleteRealmUser(TestConsts.TEST_REALM, created.getId());
    }

    @Test
    void testPatchUserAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

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
            TestConsts.TEST_REALM,
            TestConsts.TEST_REALM_ID,
            created.getId(),
            OperationType.UPDATE
        );

        // Cleanup
        deleteRealmUser(TestConsts.TEST_REALM, created.getId());
    }

    /**
     * Regression: SCIM REMOVE on a USER_PROFILE-backed attribute (externalId, displayName, etc.)
     * previously called attr.write(user, null) which passed null into List.of(value) and threw NPE,
     * returning HTTP 500 instead of a clean removal.
     */
    @Test
    void testRemoveExternalIdDoesNotNpe() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User created = new User();
        created.setUserName("remove-extid-test");
        created.setActive(true);
        created.putAdditionalProperty("externalId", "00uREMOVETEST");
        User u = scimClient.createUser(created);

        try {
            PatchRequest patch = new PatchRequest();
            patch.setSchemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
            PatchRequestOperationsInner op = new PatchRequestOperationsInner();
            op.setOp("remove");
            op.setPath("externalId");
            patch.setOperations(List.of(op));

            // Before this fix: attr.write(user, null) -> List.of(null) -> NPE -> HTTP 500.
            User after = scimClient.patchUser(u.getId(), patch);
            assertNull(after.getAdditionalProperty("externalId"));
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, u.getId());
        }
    }

    /**
     * Okta's Deactivate User action emits a PATCH without a top-level "path",
     * carrying the attribute change inside a map-valued "value" (RFC 7644
     * §3.5.2). This test covers that shape; the other tests only cover the
     * with-path form.
     */
    @Test
    void testDeactivateUserPathLessPatchOp() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create an active user
        User user = new User();
        user.setUserName("patch-pathless-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        User created = scimClient.createUser(user);
        assertNotNull(created);
        assertTrue(created.getActive());

        // Okta shape: no "path", value is a map {"active": false}
        User deactivated = scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(
                new PatchRequestOperationsInner()
                    .op("replace")
                    .value(Map.of("active", Boolean.FALSE))
            )));

        assertNotNull(deactivated);
        assertNotNull(deactivated.getActive());
        assertFalse(deactivated.getActive());

        UserRepresentation deactivatedRealmUser = findRealmUser(TestConsts.TEST_REALM, created.getId());
        assertNotNull(deactivatedRealmUser);
        assertFalse(deactivatedRealmUser.isEnabled());

        // Re-activate via the same shape to confirm the code path is symmetric
        User activated = scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(
                new PatchRequestOperationsInner()
                    .op("replace")
                    .value(Map.of("active", Boolean.TRUE))
            )));

        assertNotNull(activated);
        assertTrue(activated.getActive());

        // Cleanup
        deleteRealmUser(TestConsts.TEST_REALM, created.getId());
    }

}
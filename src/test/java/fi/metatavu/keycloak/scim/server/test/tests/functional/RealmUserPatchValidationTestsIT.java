package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UserEmailsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.UserName;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractValidationRealmScimTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for issue #123: PATCH /Users must not require mandatory
 * fields that are already set on the existing user (RFC 7644 §3.5.2 — PATCH
 * is a partial update; validators must see the post-merge state).
 */
@Testcontainers
public class RealmUserPatchValidationTestsIT extends AbstractValidationRealmScimTest {

    @Test
    void testPatchOnlyActiveDoesNotRequireMandatoryFields() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Validation realm marks firstName, lastName and email as required (roles: user).
        User user = createValidUser("patch-active-only");
        User created = scimClient.createUser(user);
        assertNotNull(created);
        assertTrue(created.getActive());

        try {
            // PATCH a single non-required attribute. The required fields are
            // already set on the existing user, so validation must pass.
            User patched = scimClient.patchUser(created.getId(), new PatchRequest()
                .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
                .operations(List.of(
                    new PatchRequestOperationsInner()
                        .op("replace")
                        .path("active")
                        .value(Boolean.FALSE)
                )));

            assertNotNull(patched);
            assertFalse(patched.getActive());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, created.getId());
        }
    }

    @Test
    void testPatchReplaceMandatoryFieldKeepsOtherMandatoryFields() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        User user = createValidUser("patch-replace-family");
        User created = scimClient.createUser(user);
        assertNotNull(created);

        try {
            // Mirrors the payload from issue #123: replace one required field
            // while leaving the others untouched in the PATCH body.
            User patched = scimClient.patchUser(created.getId(), new PatchRequest()
                .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
                .operations(List.of(
                    new PatchRequestOperationsInner()
                        .op("replace")
                        .path("active")
                        .value(Boolean.FALSE),
                    new PatchRequestOperationsInner()
                        .op("replace")
                        .path("name.familyName")
                        .value("NEW_FAMILY_NAME")
                )));

            assertNotNull(patched);
            assertFalse(patched.getActive());
            assertNotNull(patched.getName());
            assertEquals("NEW_FAMILY_NAME", patched.getName().getFamilyName());
            assertEquals("Given", patched.getName().getGivenName());
        } finally {
            deleteRealmUser(TestConsts.TEST_REALM, created.getId());
        }
    }

    private User createValidUser(String userName) {
        User user = new User();
        user.setUserName(userName);
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));

        UserName name = new UserName();
        name.setGivenName("Given");
        name.setFamilyName("Family");
        user.setName(name);

        UserEmailsInner email = new UserEmailsInner();
        email.setValue(userName + "@example.com");
        email.setPrimary(true);
        user.setEmails(List.of(email));

        return user;
    }
}

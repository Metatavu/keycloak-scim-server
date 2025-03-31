package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UserEmailsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.UserName;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.Network;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Abstract base class for SCIM tests
 */
public abstract class AbstractScimTest {

    protected static final Network network = Network.newNetwork();

    /**
     * Returns the Keycloak container
     *
     * @return Keycloak container
     */
    protected abstract KeycloakContainer getKeycloakContainer();

    /**
     * Finds user from the test realm
     *
     * @param userId user ID
     * @return user representation
     */
    protected UserRepresentation findRealmUser(String userId) {
        return getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(TestConsts.TEST_REALM)
            .users()
            .get(userId)
            .toRepresentation();
    }

    /**
     * Lists user realm role mappings
     *
     * @param userId user ID
     * @return user realm role mappings
     */
    protected List<RoleRepresentation> getUserRealmRoleMappings(String userId) {
        return getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(TestConsts.TEST_REALM)
            .users()
            .get(userId)
            .roles()
            .getAll()
            .getRealmMappings();
    }

    /**
     * Deletes user from the test realm
     *
     * @param userId user ID
     */
    protected void deleteRealmUser(String userId) {
        getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(TestConsts.TEST_REALM)
            .users()
            .get(userId)
            .remove();
    }

    /**
     * Returns SCIM URI for the test realm
     *
     * @return SCIM URI
     */
    protected URI getScimUri() {
        return URI.create(getKeycloakContainer().getAuthServerUrl()).resolve(String.format("/realms/%s/scim/v2/", TestConsts.TEST_REALM));
    }

    /**
     * Returns authenticated SCIM client
     *
     * @return authenticated SCIM client
     */
    protected ScimClient getAuthenticatedScimClient() {
        return new ScimClient(getScimUri(), getServiceAccountToken());
    }

    /**
     * Returns service account token
     *
     * @return service account token
     */
    protected String getServiceAccountToken() {
        try (Keycloak keycloakAdmin = KeycloakBuilder.builder()
            .serverUrl(getKeycloakContainer().getAuthServerUrl())
            .realm(TestConsts.TEST_REALM)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(TestConsts.TEST_SCIM_CLIENT_ID)
            .clientSecret(TestConsts.TEST_SCIM_CLIENT_SECRET)
            .build()) {

            return keycloakAdmin
                .tokenManager()
                .getAccessToken()
                .getToken();
        }
    }

    /**
     * Asserts user
     *
     * @param user user
     * @param expectedId expected ID
     * @param expectedUserName expected username
     * @param expectedGivenName expected given name
     * @param expectedFamilyName expected family name
     * @param expectedEmail expected email
     */
    protected static void assertUser(
            User user,
            String expectedId,
            String expectedUserName,
            String expectedGivenName,
            String expectedFamilyName,
            String expectedEmail,
            String expectedExternalId,
            String expectedPreferredLanguage,
            String expectedDisplayName
    ) {
        assertNotNull(user.getId());
        assertNotNull(user.getName());
        assertNotNull(user.getEmails());

        assertEquals(expectedId, user.getId());
        assertEquals(expectedUserName, user.getUserName());
        assertEquals(expectedGivenName, user.getName().getGivenName());
        assertEquals(expectedFamilyName, user.getName().getFamilyName());
        assertEquals(1, user.getEmails().size());
        assertEquals(expectedEmail, user.getEmails().getFirst().getValue());

        assertEquals(expectedExternalId, user.getExternalId());
        assertEquals(expectedPreferredLanguage, user.getPreferredLanguage());
        assertEquals(expectedDisplayName, user.getDisplayName());
    }

    /**
     * Creates a UserName object for SCIM user
     *
     * @param givenName given name
     * @param familyName family name
     * @return UserName object
     */
    @SuppressWarnings("SameParameterValue")
    protected UserName getName(
            String givenName,
            String familyName
    ) {
        UserName result = new UserName();
        result.setGivenName(givenName);
        result.setFamilyName(familyName);
        return result;
    }

    /**
     * Returns a list of email addresses for SCIM user
     *
     * @param value email address
     * @return list of email addresses
     */
    @SuppressWarnings("SameParameterValue")
    protected List<UserEmailsInner> getEmails(String value) {
        UserEmailsInner result = new UserEmailsInner();
        result.setValue(value);
        return Collections.singletonList(result);
    }

}

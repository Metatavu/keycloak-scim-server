package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UserEmailsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.UserName;
import org.keycloak.representations.idm.MemberRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.Network;

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
    protected UserRepresentation findRealmUser(String realm, String userId) {
        return getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(realm)
            .users()
            .get(userId)
            .toRepresentation();
    }

    /**
     * Finds organization member
     *
     * @param realm realm name
     * @param organizationId organization ID
     * @param userId user ID
     * @return user representation
     */
    @SuppressWarnings("SameParameterValue")
    protected MemberRepresentation findOrganizationMember(String realm, String organizationId, String userId) {
        return getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(realm)
            .organizations()
            .get(organizationId)
            .members()
            .member(userId)
            .toRepresentation();
    }

    /**
     * Lists user realm role mappings
     *
     * @param userId user ID
     * @return user realm role mappings
     */
    protected List<RoleRepresentation> getUserRealmRoleMappings(String realm, String userId) {
        return getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(realm)
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
    protected void deleteRealmUser(String realm, String userId) {
        getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(realm)
            .users()
            .get(userId)
            .remove();
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

        assertEquals(expectedExternalId, user.getAdditionalProperty("externalId"));
        assertEquals(expectedPreferredLanguage, user.getAdditionalProperty("preferredLanguage"));
        assertEquals(expectedDisplayName, user.getAdditionalProperty("displayName"));
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

package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.Group;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.GroupRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 Group create endpoint
 */
@Testcontainers
public class RealmGroupCreateTestsIT extends AbstractInternalAuthRealmScimTest {

    @Test
    void testCreateGroup() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = new Group();
        group.setDisplayName("test-group");
        group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));

        Group created = scimClient.createGroup(group);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("test-group", created.getDisplayName());
        assertEquals(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"), created.getSchemas());

        // Assert that the group was created in Keycloak
        GroupRepresentation realmGroup = findRealmGroup(TestConsts.TEST_REALM, created.getId());
        assertNotNull(realmGroup);
        assertEquals("test-group", realmGroup.getName());

        // Clean up
        deleteRealmGroup(TestConsts.TEST_REALM, realmGroup.getId());
    }

    @Test
    void testCreateGroupWithoutDisplayNameReturnsBadRequest() {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = new Group();
        group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));

        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.createGroup(group)
        );

        assertEquals(400, exception.getCode());
    }

    @Test
    void testCreateGroupWithBlankDisplayNameReturnsBadRequest() {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = new Group();
        group.setDisplayName("   ");
        group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));

        ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.createGroup(group)
        );

        assertEquals(400, exception.getCode());
    }
}

package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.GroupRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for organization default group assignment (SCIM_DEFAULT_GROUPS)
 */
@Testcontainers
public class OrganizationUserDefaultGroupsTestsIT extends AbstractOrganizationScimTest {

    @Test
    void testCreateUserJoinsDefaultGroups() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_DEFAULT_GROUPS_ID);

        User created = scimClient.createUser(createTestUser("default-groups-user"));

        // Organization configures /scim-default, /customers/org-a and /does-not-exist.
        // The unknown path is skipped without failing the provisioning
        assertEquals(List.of("/customers/org-a", "/scim-default"), getUserGroupPaths(created.getId()));

        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    @Test
    void testCreateUserWithoutDefaultGroupsJoinsNoGroups() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        User created = scimClient.createUser(createTestUser("no-default-groups-user"));

        assertEquals(List.of(), getUserGroupPaths(created.getId()));

        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    @Test
    void testUpdateUserRejoinsDefaultGroups() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_DEFAULT_GROUPS_ID);

        User created = scimClient.createUser(createTestUser("update-default-groups-user"));
        removeUserFromGroup(created.getId(), "/scim-default");
        assertEquals(List.of("/customers/org-a"), getUserGroupPaths(created.getId()));

        User replacement = createTestUser("update-default-groups-user");
        scimClient.updateUser(created.getId(), replacement);

        assertEquals(List.of("/customers/org-a", "/scim-default"), getUserGroupPaths(created.getId()));

        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    @Test
    void testPatchUserRejoinsDefaultGroups() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_DEFAULT_GROUPS_ID);

        User created = scimClient.createUser(createTestUser("patch-default-groups-user"));
        removeUserFromGroup(created.getId(), "/customers/org-a");
        assertEquals(List.of("/scim-default"), getUserGroupPaths(created.getId()));

        scimClient.patchUser(created.getId(), new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(new PatchRequestOperationsInner()
                .op("Replace")
                .path("active")
                .value(Boolean.TRUE)
            )));

        assertEquals(List.of("/customers/org-a", "/scim-default"), getUserGroupPaths(created.getId()));

        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    /**
     * Creates a SCIM user with the given username
     *
     * @param userName username
     * @return SCIM user
     */
    private User createTestUser(String userName) {
        User user = new User();
        user.setUserName(userName);
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("Default", "Groups"));
        user.setEmails(getEmails(userName + "@defaultgroupsorg.example.com"));
        return user;
    }

    /**
     * Lists sorted group paths the user is a member of
     *
     * @param userId user ID
     * @return sorted group paths
     */
    private List<String> getUserGroupPaths(String userId) {
        return getUserGroups(TestConsts.ORGANIZATIONS_REALM, userId).stream()
            .map(GroupRepresentation::getPath)
            .sorted()
            .toList();
    }

    /**
     * Removes the user from the group with the given path
     *
     * @param userId user ID
     * @param groupPath group path
     */
    private void removeUserFromGroup(String userId, String groupPath) {
        GroupRepresentation group = getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(TestConsts.ORGANIZATIONS_REALM)
            .getGroupByPath(groupPath);

        getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(TestConsts.ORGANIZATIONS_REALM)
            .users()
            .get(userId)
            .leaveGroup(group.getId());
    }
}

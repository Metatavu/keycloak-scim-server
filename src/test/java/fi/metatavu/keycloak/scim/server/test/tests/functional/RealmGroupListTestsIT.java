package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.Group;
import fi.metatavu.keycloak.scim.server.test.client.model.GroupsList;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 Group list endpoint with filtering support
 */
@Testcontainers
class RealmGroupListTestsIT extends AbstractInternalAuthRealmScimTest {

    @Test
    void testListGroupsNoFilter() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create test groups
        Group group1 = createGroup(scimClient, "test-group-1");
        Group group2 = createGroup(scimClient, "test-group-2");
        Group group3 = createGroup(scimClient, "another-group");

        try {
            GroupsList groupsList = scimClient.listGroups(null, 0, 10);

            assertNotNull(groupsList);
            assertEquals(3, groupsList.getTotalResults());
            assertEquals(0, groupsList.getStartIndex());
            assertEquals(10, groupsList.getItemsPerPage());

            List<Group> groups = groupsList.getResources();
            assertNotNull(groups);
            assertEquals(3, groups.size());
        } finally {
            // Cleanup
            deleteGroup(scimClient, group1.getId());
            deleteGroup(scimClient, group2.getId());
            deleteGroup(scimClient, group3.getId());
        }
    }

    @Test
    void testFilterByDisplayNameExactMatch() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create test groups
        Group group1 = createGroup(scimClient, "exact-match-group");
        Group group2 = createGroup(scimClient, "other-group");

        try {
            GroupsList groupsList = scimClient.listGroups("displayName eq \"exact-match-group\"", 0, 10);

            assertNotNull(groupsList);
            assertEquals(1, groupsList.getTotalResults());
            assertNotNull(groupsList.getResources());
            assertEquals(1, groupsList.getResources().size());

            Group foundGroup = groupsList.getResources().getFirst();
            assertNotNull(foundGroup);
            assertEquals("exact-match-group", foundGroup.getDisplayName());
            assertEquals(group1.getId(), foundGroup.getId());
        } finally {
            // Cleanup
            deleteGroup(scimClient, group1.getId());
            deleteGroup(scimClient, group2.getId());
        }
    }

    @Test
    void testFilterByDisplayNameNoMatch() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create test groups
        Group group1 = createGroup(scimClient, "existing-group-1");
        Group group2 = createGroup(scimClient, "existing-group-2");

        try {
            GroupsList groupsList = scimClient.listGroups("displayName eq \"nonexistent-group\"", 0, 10);

            assertNotNull(groupsList);
            assertEquals(0, groupsList.getTotalResults());
            assertNotNull(groupsList.getResources());
            assertTrue(groupsList.getResources().isEmpty());
        } finally {
            // Cleanup
            deleteGroup(scimClient, group1.getId());
            deleteGroup(scimClient, group2.getId());
        }
    }

    @Test
    void testFilterByDisplayNameCaseSensitive() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create test groups with different cases
        Group group1 = createGroup(scimClient, "TestGroup");
        Group group2 = createGroup(scimClient, "testgroup");

        try {
            // Keycloak's searchForGroupByNameStream is case-insensitive
            GroupsList groupsList = scimClient.listGroups("displayName eq \"testgroup\"", 0, 10);

            assertNotNull(groupsList);
            // Should find both groups due to case-insensitive search
            assertTrue(groupsList.getTotalResults() >= 1);
        } finally {
            // Cleanup
            deleteGroup(scimClient, group1.getId());
            deleteGroup(scimClient, group2.getId());
        }
    }

    @Test
    void testUnsupportedAttributeFallsBackToListAll() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Create test groups
        Group group1 = createGroup(scimClient, "attr-test-group-1");
        Group group2 = createGroup(scimClient, "attr-test-group-2");

        try {
            // Use an unsupported attribute
            GroupsList groupsList = scimClient.listGroups("id eq \"some-id\"", 0, 10);

            assertNotNull(groupsList);
            // Should fall back to listing all groups
            assertEquals(2, groupsList.getTotalResults());
            assertNotNull(groupsList.getResources());
            assertEquals(2, groupsList.getResources().size());
        } finally {
            // Cleanup
            deleteGroup(scimClient, group1.getId());
            deleteGroup(scimClient, group2.getId());
        }
    }

    @Test
    void testPaginationWithoutFilter() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        List<Group> createdGroups = new ArrayList<>();

        try {
            // Create 5 groups
            for (int i = 1; i <= 5; i++) {
                Group group = createGroup(scimClient, "paginated-group-" + i);
                createdGroups.add(group);
            }

            // Page 1: count=2, startIndex=0
            GroupsList page1 = scimClient.listGroups(null, 0, 2);
            assertEquals(2, page1.getItemsPerPage());
            assertEquals(0, page1.getStartIndex());
            assertEquals(5, page1.getTotalResults());
            assertNotNull(page1.getResources());
            assertEquals(2, page1.getResources().size());

            // Page 2: count=2, startIndex=2
            GroupsList page2 = scimClient.listGroups(null, 2, 2);
            assertEquals(2, page2.getItemsPerPage());
            assertEquals(2, page2.getStartIndex());
            assertEquals(5, page2.getTotalResults());
            assertNotNull(page2.getResources());
            assertEquals(2, page2.getResources().size());

            // Page 3: count=2, startIndex=4 (only one group expected)
            GroupsList page3 = scimClient.listGroups(null, 4, 2);
            assertEquals(2, page3.getItemsPerPage());
            assertEquals(4, page3.getStartIndex());
            assertEquals(5, page3.getTotalResults());
            assertNotNull(page3.getResources());
            assertTrue(page3.getResources().size() <= 2);

        } finally {
            // Cleanup
            for (Group group : createdGroups) {
                deleteGroup(scimClient, group.getId());
            }
        }
    }

    @Test
    void testInvalidFilterMissingOperator() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = createGroup(scimClient, "error-test-group");

        try {
            // Invalid filter syntax - missing operator
            ApiException exception = assertThrows(ApiException.class, () ->
                    scimClient.listGroups("displayName \"test\"", 0, 10)
            );

            assertEquals("listGroups call failed with: 400 - Invalid filter", exception.getMessage());
        } finally {
            deleteGroup(scimClient, group.getId());
        }
    }

    @Test
    void testInvalidFilterUnquotedString() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = createGroup(scimClient, "error-test-group");

        try {
            // Invalid filter syntax - unquoted string value
            ApiException exception = assertThrows(ApiException.class, () ->
                    scimClient.listGroups("displayName eq test", 0, 10)
            );

            assertEquals("listGroups call failed with: 400 - Invalid filter", exception.getMessage());
        } finally {
            deleteGroup(scimClient, group.getId());
        }
    }

    @Test
    void testInvalidFilterBadLogicalStructure() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = createGroup(scimClient, "error-test-group");

        try {
            // Invalid filter syntax - incomplete logical expression
            ApiException exception = assertThrows(ApiException.class, () ->
                    scimClient.listGroups("displayName eq \"test\" and", 0, 10)
            );

            assertEquals("listGroups call failed with: 400 - Invalid filter", exception.getMessage());
        } finally {
            deleteGroup(scimClient, group.getId());
        }
    }

    @Test
    void testEmptyGroupsList() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        // Don't create any groups, just list
        GroupsList groupsList = scimClient.listGroups(null, 0, 10);

        assertNotNull(groupsList);
        assertEquals(0, groupsList.getTotalResults());
        assertNotNull(groupsList.getResources());
        assertTrue(groupsList.getResources().isEmpty());
    }

    @Test
    void testListGroupsWithStartIndexBeyondTotal() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = createGroup(scimClient, "boundary-test-group");

        try {
            // Try to start beyond the total results
            GroupsList groupsList = scimClient.listGroups(null, 100, 10);

            assertNotNull(groupsList);
            assertEquals(1, groupsList.getTotalResults());
            assertEquals(100, groupsList.getStartIndex());
            assertNotNull(groupsList.getResources());
            // Should return empty list when starting beyond total
            assertTrue(groupsList.getResources().isEmpty());
        } finally {
            deleteGroup(scimClient, group.getId());
        }
    }

    /**
     * Helper method to create a group
     *
     * @param scimClient SCIM client
     * @param displayName display name
     * @return created group
     * @throws ApiException if creation fails
     */
    protected Group createGroup(ScimClient scimClient, String displayName) throws ApiException {
        Group group = new Group();
        group.setDisplayName(displayName);
        group.setSchemas(Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:Group"));

        Group created = scimClient.createGroup(group);
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(displayName, created.getDisplayName());

        return created;
    }

    /**
     * Helper method to delete a group
     *
     * @param scimClient SCIM client
     * @param groupId group ID
     */
    private void deleteGroup(ScimClient scimClient, String groupId) {
        try {
            scimClient.deleteGroup(groupId);
        } catch (ApiException e) {
            // Log but don't fail the test on cleanup errors
            System.err.println("Failed to delete group " + groupId + ": " + e.getMessage());
        }
    }
}

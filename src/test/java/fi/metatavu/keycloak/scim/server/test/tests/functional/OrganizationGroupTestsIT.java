package fi.metatavu.keycloak.scim.server.test.tests.functional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.Group;
import fi.metatavu.keycloak.scim.server.test.client.model.GroupMembersInner;
import fi.metatavu.keycloak.scim.server.test.client.model.GroupsList;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 groups on the organization endpoint, which manage Keycloak organization groups
 */
@Testcontainers
public class OrganizationGroupTestsIT extends AbstractOrganizationScimTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Test
    void testCreateGroupCreatesOrganizationGroup() throws Exception {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        Group created = createGroup(scimClient, "org-create");
        assertNotNull(created.getId());
        assertEquals("org-create", created.getDisplayName());
        assertEquals(
            getScimUri(TestConsts.ORGANIZATION_1_ID).resolve(String.format("Groups/%s", created.getId())),
            created.getMeta().getLocation()
        );

        assertTrue(getOrganizationGroupIds(TestConsts.ORGANIZATION_1_ID).contains(created.getId()));
        assertFalse(getOrganizationGroupIds(TestConsts.ORGANIZATION_2_ID).contains(created.getId()));
        assertTrue(getRealmGroupIds().isEmpty());

        scimClient.deleteGroup(created.getId());
    }

    @Test
    void testCreateDuplicateGroupConflicts() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        Group created = createGroup(scimClient, "org-duplicate");

        ApiException exception = assertThrows(ApiException.class, () -> createGroup(scimClient, "org-duplicate"));
        assertEquals(409, exception.getCode());

        scimClient.deleteGroup(created.getId());
    }

    @Test
    void testSameGroupNameInTwoOrganizations() throws ApiException {
        ScimClient org1Client = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        ScimClient org2Client = getAuthenticatedScimClient(TestConsts.ORGANIZATION_2_ID);

        Group org1Group = createGroup(org1Client, "org-shared-name");
        Group org2Group = createGroup(org2Client, "org-shared-name");
        assertNotEquals(org1Group.getId(), org2Group.getId());

        GroupsList org1List = org1Client.listGroups("displayName eq \"org-shared-name\"", 1, 10);
        assertEquals(1, org1List.getTotalResults());
        assertEquals(org1Group.getId(), org1List.getResources().getFirst().getId());

        org1Client.deleteGroup(org1Group.getId());
        org2Client.deleteGroup(org2Group.getId());
    }

    @Test
    void testGroupOfOtherOrganizationIsNotFound() throws ApiException {
        ScimClient org1Client = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        ScimClient org2Client = getAuthenticatedScimClient(TestConsts.ORGANIZATION_2_ID);
        Group org1Group = createGroup(org1Client, "org-foreign");

        assertEquals(404, assertThrows(ApiException.class, () -> org2Client.findGroup(org1Group.getId())).getCode());
        assertEquals(404, assertThrows(ApiException.class, () -> org2Client.updateGroup(org1Group.getId(), new Group().displayName("hijacked"))).getCode());
        assertEquals(404, assertThrows(ApiException.class, () -> org2Client.patchGroup(org1Group.getId(), replaceDisplayName("hijacked"))).getCode());
        assertEquals(404, assertThrows(ApiException.class, () -> org2Client.deleteGroup(org1Group.getId())).getCode());

        GroupsList org2Groups = org2Client.listGroups(null, 1, 100);
        assertTrue(org2Groups.getResources().stream().noneMatch(group -> group.getId().equals(org1Group.getId())));

        Group unchanged = org1Client.findGroup(org1Group.getId());
        assertEquals("org-foreign", unchanged.getDisplayName());

        org1Client.deleteGroup(org1Group.getId());
    }

    @Test
    void testEntraProvisioningFlow() throws Exception {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        User user = createUser(scimClient, "org-entra-user", "Entra", "User");

        Group group = createGroup(scimClient, "org-entra-group");

        assertEquals(204, scimClient.patchGroup(group.getId(), membersOperation("Add", user.getId())).status());
        assertEquals(Set.of(user.getId()), getOrganizationGroupMemberIds(TestConsts.ORGANIZATION_1_ID, group.getId()));

        GroupsList filtered = scimClient.listGroups("displayName eq \"org-entra-group\"", 1, 10, null, "members");
        assertEquals(1, filtered.getTotalResults());
        assertNull(filtered.getResources().getFirst().getMembers());

        assertEquals(204, scimClient.patchGroup(group.getId(), replaceDisplayName("org-entra-renamed")).status());
        assertEquals("org-entra-renamed", scimClient.findGroup(group.getId()).getDisplayName());

        assertEquals(204, scimClient.patchGroup(group.getId(), membersOperation("Remove", user.getId())).status());
        assertTrue(getOrganizationGroupMemberIds(TestConsts.ORGANIZATION_1_ID, group.getId()).isEmpty());
        assertTrue(findOrganizationMember(TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID, user.getId()) != null);

        scimClient.deleteGroup(group.getId());
        assertFalse(getOrganizationGroupIds(TestConsts.ORGANIZATION_1_ID).contains(group.getId()));
        assertEquals(404, assertThrows(ApiException.class, () -> scimClient.findGroup(group.getId())).getCode());

        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, user.getId());
    }

    @Test
    void testReplaceMembersWithPut() throws Exception {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        User first = createUser(scimClient, "org-put-first", "Put", "First");
        User second = createUser(scimClient, "org-put-second", "Put", "Second");
        Group group = createGroup(scimClient, "org-put");

        scimClient.patchGroup(group.getId(), membersOperation("add", first.getId()));

        Group replaced = scimClient.updateGroup(group.getId(), new Group()
            .schemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"))
            .displayName("org-put-renamed")
            .members(List.of(new GroupMembersInner().value(second.getId()))));

        assertEquals("org-put-renamed", replaced.getDisplayName());
        assertEquals(Set.of(second.getId()), getOrganizationGroupMemberIds(TestConsts.ORGANIZATION_1_ID, group.getId()));

        scimClient.deleteGroup(group.getId());
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, first.getId());
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, second.getId());
    }

    @Test
    void testAddUserOutsideOrganizationFails() throws Exception {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        String outsiderId = createRealmUser(TestConsts.ORGANIZATIONS_REALM, "org-outsider", "Org", "Outsider");
        grantRealmRole(TestConsts.ORGANIZATIONS_REALM, outsiderId, "scim-managed");
        Group group = createGroup(scimClient, "org-outsider-group");

        ApiException exception = assertThrows(ApiException.class, () -> scimClient.patchGroup(group.getId(), membersOperation("add", outsiderId)));
        assertEquals(400, exception.getCode());
        assertTrue(getOrganizationGroupMemberIds(TestConsts.ORGANIZATION_1_ID, group.getId()).isEmpty());

        scimClient.deleteGroup(group.getId());
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, outsiderId);
    }

    @Test
    void testAddMemberOfOtherOrganizationFails() throws Exception {
        ScimClient org1Client = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        ScimClient org2Client = getAuthenticatedScimClient(TestConsts.ORGANIZATION_2_ID);
        User org2User = createUser(org2Client, "org2-member", "Org2", "Member");
        Group org1Group = createGroup(org1Client, "org1-only");

        ApiException exception = assertThrows(ApiException.class, () -> org1Client.patchGroup(org1Group.getId(), membersOperation("add", org2User.getId())));
        assertEquals(400, exception.getCode());
        assertTrue(getOrganizationGroupMemberIds(TestConsts.ORGANIZATION_1_ID, org1Group.getId()).isEmpty());

        org1Client.deleteGroup(org1Group.getId());
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, org2User.getId());
    }

    @Test
    void testFailedPatchIsRolledBack() throws Exception {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        User user = createUser(scimClient, "org-rollback-user", "Rollback", "User");
        Group group = createGroup(scimClient, "org-rollback");

        PatchRequest patchRequest = new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(
                membersOperation("add", user.getId()).getOperations().getFirst(),
                membersOperation("add", "unknown-user-id").getOperations().getFirst()
            ));

        assertEquals(400, assertThrows(ApiException.class, () -> scimClient.patchGroup(group.getId(), patchRequest)).getCode());
        assertTrue(getOrganizationGroupMemberIds(TestConsts.ORGANIZATION_1_ID, group.getId()).isEmpty());

        scimClient.deleteGroup(group.getId());
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, user.getId());
    }

    @Test
    void testOrganizationGroupAdminEvents() throws Exception {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        User user = createUser(scimClient, "org-event-user", "Event", "User");
        clearAdminEvents();

        Group group = createGroup(scimClient, "org-event-group");
        scimClient.patchGroup(group.getId(), membersOperation("add", user.getId()));
        scimClient.patchGroup(group.getId(), membersOperation("remove", user.getId()));
        scimClient.deleteGroup(group.getId());

        String groupPath = String.format("organizations/%s/groups/%s", TestConsts.ORGANIZATION_1_ID, group.getId());
        String membershipPath = groupPath + "/members/" + user.getId();
        List<AdminEvent> adminEvents = getAdminEvents();

        assertAdminEvent(adminEvents, ResourceType.ORGANIZATION_GROUP, OperationType.CREATE, groupPath);
        assertAdminEvent(adminEvents, ResourceType.ORGANIZATION_GROUP_MEMBERSHIP, OperationType.CREATE, membershipPath);
        assertAdminEvent(adminEvents, ResourceType.ORGANIZATION_GROUP_MEMBERSHIP, OperationType.DELETE, membershipPath);
        assertAdminEvent(adminEvents, ResourceType.ORGANIZATION_GROUP, OperationType.DELETE, groupPath);
        assertTrue(adminEvents.stream().noneMatch(event -> event.getResourceType() == ResourceType.GROUP || event.getResourceType() == ResourceType.GROUP_MEMBERSHIP));

        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, user.getId());
    }

    private void assertAdminEvent(List<AdminEvent> adminEvents, ResourceType resourceType, OperationType operationType, String resourcePath) {
        assertEquals(1, adminEvents.stream()
            .filter(event -> event.getResourceType() == resourceType && event.getOperationType() == operationType)
            .filter(event -> resourcePath.equals(event.getResourcePath()))
            .count(), String.format("%s %s %s", operationType, resourceType, resourcePath));
    }

    private PatchRequest membersOperation(String op, String userId) {
        return new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(new PatchRequestOperationsInner()
                .op(op)
                .path("members")
                .value(List.of(Map.of("value", userId)))));
    }

    private PatchRequest replaceDisplayName(String displayName) {
        return new PatchRequest()
            .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
            .operations(List.of(new PatchRequestOperationsInner()
                .op("Replace")
                .path("displayName")
                .value(displayName)));
    }

    private Set<String> getOrganizationGroupIds(String organizationId) throws IOException, InterruptedException {
        return getIds(adminGet(String.format("organizations/%s/groups?search=%s", organizationId, URLEncoder.encode("*", StandardCharsets.UTF_8))));
    }

    private Set<String> getOrganizationGroupMemberIds(String organizationId, String groupId) throws IOException, InterruptedException {
        return getIds(adminGet(String.format("organizations/%s/groups/%s/members", organizationId, groupId)));
    }

    private Set<String> getRealmGroupIds() throws IOException, InterruptedException {
        return getIds(adminGet("groups"));
    }

    private Set<String> getIds(JsonNode array) {
        Set<String> ids = new HashSet<>();
        array.forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    /**
     * Reads the Keycloak admin REST API directly, since the admin client on the test classpath
     * predates organization groups
     */
    private JsonNode adminGet(String path) throws IOException, InterruptedException {
        String token = getKeycloakContainer().getKeycloakAdminClient().tokenManager().getAccessToken().getToken();
        URI uri = URI.create(getKeycloakContainer().getAuthServerUrl())
            .resolve(String.format("/admin/realms/%s/%s", TestConsts.ORGANIZATIONS_REALM, path));

        HttpResponse<String> response = HTTP_CLIENT.send(
            HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + token).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode(), response.body());
        return OBJECT_MAPPER.readTree(response.body());
    }

}

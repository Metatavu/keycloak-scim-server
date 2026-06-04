package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.Group;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.representations.idm.GroupRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 Group create endpoint and admin events
 */
@Testcontainers
public class RealmGroupCreateTestsIT extends AbstractInternalAuthRealmScimTest {

    @Test
    void testCreateGroupWithExternalId() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        String externalId = "urn:mace:surf.nl:invite.test.surfconext.nl:4f9831b6-dfa9-4113-a697-0c9f62ff9ace:harry";

        Group group = new Group();
        group.setDisplayName("external-id-group");
        group.setExternalId(externalId);
        group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));

        Group created = scimClient.createGroup(group);

        assertNotNull(created);
        assertEquals(externalId, created.getExternalId());

        GroupRepresentation realmGroup = findRealmGroup(TestConsts.TEST_REALM, created.getId());
        assertNotNull(realmGroup);
        assertEquals(externalId, realmGroup.getAttributes().get("externalId").getFirst());

        Group fetched = scimClient.findGroup(created.getId());
        assertEquals(externalId, fetched.getExternalId());

        deleteRealmGroup(TestConsts.TEST_REALM, realmGroup.getId());
    }

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
    void testCreateGroupAdminEvents() throws ApiException, IOException {
        ScimClient scimClient = getAuthenticatedScimClient();

        Group group = new Group();
        group.setDisplayName("event-test-group");
        group.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));

        group = scimClient.createGroup(group);

        List<AdminEvent> adminEvents = getAdminEvents();
        assertEquals(1, adminEvents.size());

        AdminEvent createGroupEvent = adminEvents.stream()
            .filter(event -> event.getResourceType() == ResourceType.GROUP)
            .findFirst()
            .orElse(null);

        assertGroupAdminEvent(
            createGroupEvent,
            TestConsts.TEST_REALM,
            TestConsts.TEST_REALM_ID,
            group.getId(),
            OperationType.CREATE
        );

        // Clean up
        deleteRealmGroup(TestConsts.TEST_REALM, group.getId());
    }
  
    @Test
    void testCreateGroupWithApplicationJsonContentType() throws Exception {
        String body = """
            {
              "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"],
              "displayName": "json-content-type-group"
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(getScimUri().resolve("Groups"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Authorization", "Bearer " + getServiceAccountToken())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build();

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        assertEquals(201, response.statusCode(), () -> response.body());

        JsonNode created = new ObjectMapper().readTree(response.body());
        String groupId = created.get("id").asText();
        assertEquals("json-content-type-group", created.get("displayName").asText());

        deleteRealmGroup(TestConsts.TEST_REALM, groupId);
    }

    @Test
    void testCreateGroupWithApplicationScimContentType() throws Exception {
        String body = """
            {
              "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"],
              "displayName": "scim-content-type-group"
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(getScimUri().resolve("Groups"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Authorization", "Bearer " + getServiceAccountToken())
            .header("Content-Type", "application/scim")
            .header("Accept", "application/scim")
            .build();

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        assertEquals(201, response.statusCode(), () -> response.body());

        JsonNode created = new ObjectMapper().readTree(response.body());
        deleteRealmGroup(TestConsts.TEST_REALM, created.get("id").asText());
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

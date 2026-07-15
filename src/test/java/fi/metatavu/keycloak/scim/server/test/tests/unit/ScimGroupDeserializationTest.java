package fi.metatavu.keycloak.scim.server.test.tests.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.User;
import org.junit.jupiter.api.Test;
import org.keycloak.util.JsonSerialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ScimGroupDeserializationTest {

    private static final String GROUP_JSON = """
        {
          "meta": {
            "location": "/Groups/bad1bda1-62c7-46eb-ad0d-62e4d65d1ef8",
            "resourceType": "Group",
            "lastModified": "2026-04-30T14:12:50.262230"
          },
          "schemas": [
            "urn:ietf:params:scim:schemas:core:2.0:Group"
          ],
          "externalId": "urn:mace:surf.nl:invite.test.surfconext.nl:4f9831b6-dfa9-4113-a697-0c9f62ff9ace:harry",
          "displayName": "Demo Group",
          "members": []
        }
        """;

    @Test
    void deserializeGroupCreatePayloadWithJackson() throws Exception {
        Group group = new ObjectMapper().readValue(GROUP_JSON, Group.class);

        assertEquals("Demo Group", group.getDisplayName());
        assertEquals("urn:mace:surf.nl:invite.test.surfconext.nl:4f9831b6-dfa9-4113-a697-0c9f62ff9ace:harry", group.getExternalId());
        assertNotNull(group.getMembers());
    }

    @Test
    void deserializeGroupCreatePayloadWithKeycloakJsonSerialization() throws Exception {
        Group group = JsonSerialization.readValue(GROUP_JSON, Group.class);

        assertEquals("Demo Group", group.getDisplayName());
        assertEquals("urn:mace:surf.nl:invite.test.surfconext.nl:4f9831b6-dfa9-4113-a697-0c9f62ff9ace:harry", group.getExternalId());
        assertNotNull(group.getMembers());
    }

    @Test
    void deserializeUserCreatePayloadWithKeycloakJsonSerialization() throws Exception {
        String json = """
            {
              "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
              "userName": "alice@example.com",
              "externalId": "ext-1",
              "displayName": "Alice"
            }
            """;

        User user = JsonSerialization.readValue(json, User.class);

        assertEquals("alice@example.com", user.getUserName());
        assertEquals("ext-1", user.getExternalId());
        assertEquals("Alice", user.getAdditionalProperty("displayName"));
    }
}
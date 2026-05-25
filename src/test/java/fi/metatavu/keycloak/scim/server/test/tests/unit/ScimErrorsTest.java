package fi.metatavu.keycloak.scim.server.test.tests.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.scim.server.ScimErrors;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScimErrorsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void encodesPlainDetail() throws Exception {
        Response r = ScimErrors.badRequest("Unsupported attribute: members.value");
        assertEquals(400, r.getStatus());
        assertEquals("application/scim+json", r.getMediaType().toString());
        JsonNode body = MAPPER.readTree((String) r.getEntity());
        assertTrue(body.get("schemas").toString().contains("urn:ietf:params:scim:api:messages:2.0:Error"));
        assertEquals("400", body.get("status").asText());
        assertEquals("Unsupported attribute: members.value", body.get("detail").asText());
    }

    @Test
    void encodesDetailWithControlChars() throws Exception {
        // Reviewer point #2: a newline / tab in detail must not produce malformed JSON.
        Response r = ScimErrors.badRequest("Unsupported attribute: weird\n\tpath\"with\\quotes");
        JsonNode body = MAPPER.readTree((String) r.getEntity());
        assertEquals("Unsupported attribute: weird\n\tpath\"with\\quotes", body.get("detail").asText());
    }

    @Test
    void nullDetailIsEmptyString() throws Exception {
        Response r = ScimErrors.notFound(null);
        JsonNode body = MAPPER.readTree((String) r.getEntity());
        assertEquals("", body.get("detail").asText());
    }
}

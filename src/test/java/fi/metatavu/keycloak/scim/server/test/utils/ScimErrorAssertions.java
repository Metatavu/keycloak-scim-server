package fi.metatavu.keycloak.scim.server.test.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ScimErrorAssertions {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

    private ScimErrorAssertions() {}

    /**
     * Assert the ApiException carries a SCIM 2.0 Error JSON body with the
     * given HTTP status and a detail field that contains {@code detailSubstring}.
     */
    public static void assertScimError(ApiException ex, int expectedStatus, String detailSubstring) {
        assertEquals(expectedStatus, ex.getCode(),
                "HTTP status mismatch; body=" + ex.getResponseBody());
        JsonNode body;
        try {
            body = MAPPER.readTree(ex.getResponseBody());
        } catch (Exception e) {
            throw new AssertionError("ApiException body is not valid JSON: " + ex.getResponseBody(), e);
        }
        assertTrue(body.path("schemas").toString().contains(ERROR_SCHEMA),
                "schemas should contain Error URN; got: " + body.path("schemas"));
        assertEquals(Integer.toString(expectedStatus), body.path("status").asText(),
                "SCIM Error status field mismatch; body=" + body);
        assertTrue(body.path("detail").asText().contains(detailSubstring),
                "detail should contain '" + detailSubstring + "'; got: " + body.path("detail"));
    }
}

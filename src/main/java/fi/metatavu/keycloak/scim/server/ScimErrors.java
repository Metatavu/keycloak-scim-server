package fi.metatavu.keycloak.scim.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;

/**
 * Helper for building SCIM 2.0 Error responses (RFC 7644 §3.12).
 *
 * Existing call sites returned plain-text bodies (e.g. "Unsupported group path",
 * "Missing userName"), which broke clients that strictly parse error responses
 * as JSON (Okta, Entra ID). All error responses go through this helper now and
 * return a valid SCIM Error JSON document with the application/scim+json media
 * type.
 *
 * The body is built via Jackson so any control character or quote that arrives
 * in {@code detail} (typically from a user-supplied attribute path interpolated
 * into an error message) is escaped correctly. A hand-rolled escape used to
 * cover only `\\` and `"` and reintroduced the JSON parse failure on the client
 * side as soon as a `\\n` or `\\t` reached `detail`.
 */
public final class ScimErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

    private ScimErrors() {
        // utility class
    }

    /**
     * Build a SCIM 2.0 Error response.
     *
     * @param status   HTTP status (e.g. BAD_REQUEST)
     * @param scimType RFC 7644 §3.12 scimType value; null omits the field
     * @param detail   human-readable error detail; null is rendered as the empty string
     * @return Response carrying a SCIM Error JSON body and application/scim+json type
     */
    public static Response error(Response.Status status, String scimType, String detail) {
        ObjectNode node = MAPPER.createObjectNode();
        node.putArray("schemas").add(ERROR_SCHEMA);
        node.put("status", Integer.toString(status.getStatusCode()));
        if (scimType != null) {
            node.put("scimType", scimType);
        }
        node.put("detail", detail == null ? "" : detail);
        String body;
        try {
            body = MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            // ObjectNode is always serializable; fall back to a static body if Jackson
            // somehow fails so we still return SCIM-shaped JSON.
            body = "{\"schemas\":[\"" + ERROR_SCHEMA + "\"],\"status\":\""
                    + status.getStatusCode() + "\",\"detail\":\"\"}";
        }
        return Response.status(status).type("application/scim+json").entity(body).build();
    }

    /**
     * Convenience for HTTP 400 errors without a semantic scimType.
     */
    public static Response badRequest(String detail) {
        return error(Response.Status.BAD_REQUEST, null, detail);
    }

    /**
     * HTTP 400 — scimType=invalidValue (attribute value fails profile validation).
     */
    public static Response invalidValue(String detail) {
        return error(Response.Status.BAD_REQUEST, "invalidValue", detail);
    }

    /**
     * HTTP 400 — scimType=invalidFilter (filter expression is invalid or unsupported).
     */
    public static Response invalidFilter(String detail) {
        return error(Response.Status.BAD_REQUEST, "invalidFilter", detail);
    }

    /**
     * HTTP 400 — scimType=invalidPath (path attribute in PATCH is invalid or malformed).
     */
    public static Response invalidPath(String detail) {
        return error(Response.Status.BAD_REQUEST, "invalidPath", detail);
    }

    /**
     * HTTP 400 — scimType=invalidSyntax (request body or operation is malformed).
     */
    public static Response invalidSyntax(String detail) {
        return error(Response.Status.BAD_REQUEST, "invalidSyntax", detail);
    }

    /**
     * Convenience for HTTP 404 errors.
     */
    public static Response notFound(String detail) {
        return error(Response.Status.NOT_FOUND, null, detail);
    }

    /**
     * HTTP 400 — scimType=mutability (PATCH attempted on a read-only or immutable attribute).
     */
    public static Response mutability(String detail) {
        return error(Response.Status.BAD_REQUEST, "mutability", detail);
    }

    /**
     * HTTP 409 — scimType=uniqueness (uniqueness constraint violated).
     */
    public static Response conflict(String detail) {
        return error(Response.Status.CONFLICT, "uniqueness", detail);
    }

    /**
     * Convenience for HTTP 403 errors.
     */
    public static Response forbidden(String detail) {
        return error(Response.Status.FORBIDDEN, null, detail);
    }
}

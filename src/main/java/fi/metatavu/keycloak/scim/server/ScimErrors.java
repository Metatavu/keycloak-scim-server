package fi.metatavu.keycloak.scim.server;

import jakarta.ws.rs.core.Response;

/**
 * Helper for building SCIM 2.0 Error responses (RFC 7644 §3.12).
 *
 * Existing call sites returned plain-text bodies (e.g. "Unsupported group path",
 * "Missing userName"), which broke clients that strictly parse error responses
 * as JSON (Okta, Entra ID). All error responses go through this helper now and
 * return a valid SCIM Error JSON document with the application/scim+json media
 * type.
 */
public final class ScimErrors {

    private ScimErrors() {
        // utility class
    }

    /**
     * Build a SCIM 2.0 Error response.
     *
     * @param status HTTP status (e.g. BAD_REQUEST)
     * @param detail human-readable error detail
     * @return Response carrying a SCIM Error JSON body and application/scim+json type
     */
    public static Response error(Response.Status status, String detail) {
        String safeDetail = detail == null
                ? ""
                : detail.replace("\\", "\\\\").replace("\"", "\\\"");
        String body = "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"]"
                + ",\"status\":\"" + status.getStatusCode() + "\""
                + ",\"detail\":\"" + safeDetail + "\"}";
        return Response.status(status).type("application/scim+json").entity(body).build();
    }

    /**
     * Convenience for HTTP 400 errors.
     */
    public static Response badRequest(String detail) {
        return error(Response.Status.BAD_REQUEST, detail);
    }

    /**
     * Convenience for HTTP 404 errors.
     */
    public static Response notFound(String detail) {
        return error(Response.Status.NOT_FOUND, detail);
    }

    /**
     * Convenience for HTTP 409 errors.
     */
    public static Response conflict(String detail) {
        return error(Response.Status.CONFLICT, detail);
    }

    /**
     * Convenience for HTTP 403 errors.
     */
    public static Response forbidden(String detail) {
        return error(Response.Status.FORBIDDEN, detail);
    }
}

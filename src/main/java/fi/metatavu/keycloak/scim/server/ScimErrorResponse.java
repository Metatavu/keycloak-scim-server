package fi.metatavu.keycloak.scim.server;

import jakarta.ws.rs.core.Response;

public final class ScimErrorResponse {

    private static final String SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

    private ScimErrorResponse() {}

    public static Response scimError(Response.Status status, String scimType, String detail) {
        String json = "{\"schemas\":[\"" + SCHEMA + "\"],"
            + "\"status\":\"" + status.getStatusCode() + "\""
            + (scimType != null ? ",\"scimType\":\"" + scimType + "\"" : "")
            + ",\"detail\":\"" + detail.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        return Response.status(status).entity(json).type("application/scim+json").build();
    }
}

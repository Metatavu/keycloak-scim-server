package fi.metatavu.keycloak.scim.server.util;

import fi.metatavu.keycloak.scim.server.model.ErrorResponse;

import jakarta.ws.rs.core.Response;

import java.util.List;

public class ScimResponseUtil {

    private static final String SCHEMA_ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";

    public static Response scimError(Response.Status status, String detail) {
        ErrorResponse error = new ErrorResponse();
        error.setSchemas(List.of(SCHEMA_ERROR));
        error.setDetail(detail);
        error.setStatus(String.valueOf(status.getStatusCode()));

        return Response.status(status)
            .entity(error)
            .build();
    }
}
package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import fi.metatavu.keycloak.scim.server.users.UserProfileValidationException;
import jakarta.ws.rs.core.Response;

/**
 * Functional interface for user write operations that may throw SCIM-specific checked exceptions.
 */
@FunctionalInterface
public interface UserOperation {
    Response execute() throws UnsupportedPatchOperation, UserProfileValidationException;
}

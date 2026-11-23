package fi.metatavu.keycloak.scim.server.groups;

/**
 * Thrown when a SCIM PATCH operation on a Group carries a value that cannot be
 * applied, such as an "add" or "replace" operation without a "value". Mapped to
 * a 400 invalidValue error and fails the whole PATCH request.
 */
public class InvalidGroupPatchValue extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidGroupPatchValue(String message) {
        super(message);
    }
}

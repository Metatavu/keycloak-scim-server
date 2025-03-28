package fi.metatavu.keycloak.scim.server.patch;

/**
 * Exception thrown when an unsupported or invalid SCIM patch operation is encountered
 */
public class UnsupportedPatchOperation extends Exception {

    private final String operation;

    public UnsupportedPatchOperation(String operation) {
        super("Unsupported or invalid SCIM patch operation: " + operation);
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }
}

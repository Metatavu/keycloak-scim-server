package fi.metatavu.keycloak.scim.server.patch;

public enum PatchOperation {

    ADD("add"),
    REMOVE("remove"),
    REPLACE("replace");

    private final String operation;

    PatchOperation(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    public static PatchOperation fromString(String operation) {
        for (PatchOperation b : PatchOperation.values()) {
            if (b.operation.equalsIgnoreCase(operation)) {
                return b;
            }
        }
        return null;
    }
}

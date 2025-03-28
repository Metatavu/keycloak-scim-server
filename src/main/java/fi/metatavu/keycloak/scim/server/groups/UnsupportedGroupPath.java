package fi.metatavu.keycloak.scim.server.groups;

/**
 * Exception for unsupported group paths
 */
public class UnsupportedGroupPath extends Exception {

    private final String path;

    public UnsupportedGroupPath(String path) {
        super("Unsupported or invalid SCIM group path: " + path);
        this.path = path;
    }

    public String getPath() {
        return path;
    }

}

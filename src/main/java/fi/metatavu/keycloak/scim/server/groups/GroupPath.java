package fi.metatavu.keycloak.scim.server.groups;

/**
 * Enum for group paths
 */
public enum GroupPath {

    DISPLAY_NAME ("displayName"),
    MEMBERS ("members");

    private final String path;

    GroupPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public static GroupPath findByPath(String path) {
        for (GroupPath groupPath : values()) {
            if (groupPath.getPath().equals(path)) {
                return groupPath;
            }
        }

        return null;
    }

}

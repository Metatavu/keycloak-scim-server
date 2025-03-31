package fi.metatavu.keycloak.scim.server.metadata;

/**
 * Enum for group attributes
 */
public enum GroupAttribute {

    DISPLAY_NAME ("displayName"),
    MEMBERS ("members");

    private final String scimPath;

    GroupAttribute(String scimPath) {
        this.scimPath = scimPath;
    }

    public String getScimPath() {
        return scimPath;
    }

    public static GroupAttribute findByScimPath(String scimPath) {
        for (GroupAttribute groupAttribute : values()) {
            if (groupAttribute.getScimPath().equals(scimPath)) {
                return groupAttribute;
            }
        }

        return null;
    }

}

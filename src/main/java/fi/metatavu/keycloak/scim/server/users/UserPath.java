package fi.metatavu.keycloak.scim.server.users;

import fi.metatavu.keycloak.scim.server.model.SchemaAttribute.TypeEnum;
import fi.metatavu.keycloak.scim.server.model.SchemaAttribute.MutabilityEnum;
import fi.metatavu.keycloak.scim.server.model.SchemaAttribute.UniquenessEnum;

/**
 * User attributes
 */
public enum UserPath {

    USERNAME ("userName", "User name", TypeEnum.STRING, MutabilityEnum.READWRITE, UniquenessEnum.SERVER),
    EMAIL ("email", "Email", TypeEnum.STRING, MutabilityEnum.READWRITE, UniquenessEnum.SERVER),
    GIVEN_NAME ("name.givenName", "First name", TypeEnum.STRING, MutabilityEnum.READWRITE, UniquenessEnum.NONE),
    FAMILY_NAME ("name.familyName", "Family name", TypeEnum.STRING, MutabilityEnum.READWRITE, UniquenessEnum.NONE),
    ACTIVE ("active", "Whether user is active", TypeEnum.BOOLEAN, MutabilityEnum.READWRITE, UniquenessEnum.NONE);

    private final String name;
    private final String description;
    private final TypeEnum type;
    private final MutabilityEnum mutability;
    private final UniquenessEnum uniqueness;

    UserPath(String name, String description, TypeEnum type, MutabilityEnum mutability, UniquenessEnum uniqueness) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.mutability = mutability;
        this.uniqueness = uniqueness;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TypeEnum getType() {
        return type;
    }

    public MutabilityEnum getMutability() {
        return mutability;
    }

    public UniquenessEnum getUniqueness() {
        return uniqueness;
    }

    /**
     * Finds user attribute by name
     *
     * @param name attribute name
     * @return user attribute or null if not found
     */
    public static UserPath findByName(String name) {
        for (UserPath userAttribute : values()) {
            if (userAttribute.getName().equals(name)) {
                return userAttribute;
            }
        }

        return null;
    }

}
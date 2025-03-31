package fi.metatavu.keycloak.scim.server.metadata;

import fi.metatavu.keycloak.scim.server.model.SchemaAttribute;

public class UserAttributeMapping {

    public enum Source {
        USER_MODEL,
        USER_PROFILE
    }

    private final Source source;
    private final String sourceId;
    private final String scimPath;
    private final String description;
    private final SchemaAttribute.TypeEnum type;
    private final SchemaAttribute.MutabilityEnum mutability;
    private final SchemaAttribute.UniquenessEnum uniqueness;

    public UserAttributeMapping(
            Source source,
            String sourceId,
            String scimPath,
            String description,
            SchemaAttribute.TypeEnum type,
            SchemaAttribute.MutabilityEnum mutability,
            SchemaAttribute.UniquenessEnum uniqueness
    ) {
        this.source = source;
        this.sourceId = sourceId;
        this.scimPath = scimPath;
        this.description = description;
        this.type = type;
        this.mutability = mutability;
        this.uniqueness = uniqueness;
    }

    public Source getSource() {
        return source;
    }

    public String getScimPath() {
        return scimPath;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDescription() {
        return description;
    }

    public SchemaAttribute.TypeEnum getType() {
        return type;
    }

    public SchemaAttribute.MutabilityEnum getMutability() {
        return mutability;
    }

    public SchemaAttribute.UniquenessEnum getUniqueness() {
        return uniqueness;
    }

}

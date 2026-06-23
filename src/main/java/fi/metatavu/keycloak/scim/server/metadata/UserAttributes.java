package fi.metatavu.keycloak.scim.server.metadata;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User attributes
 */
public class UserAttributes {

    private final Map<String, UserAttribute<?>> attributeMap;

    /**
     * Constructor
     *
     * @param attributes attributes
     */
    UserAttributes(List<UserAttribute<?>> attributes) {
        this.attributeMap = attributes.stream()
            .collect(Collectors.toMap(UserAttribute::getScimPath, Function.identity()));
    }

    /**
     * Finds user attribute by SCIM path
     *
     * @param scimPath SCIM path
     * @return user attribute or null if not found
     */
    public UserAttribute<?> findByScimPath(String scimPath) {
        return attributeMap.get(scimPath);
    }

    /**
     * Returns all configured user attributes.
     *
     * @return all user attributes
     */
    public List<UserAttribute<?>> list() {
        return List.copyOf(attributeMap.values());
    }

    /**
     * Lists user attributes by source
     *
     * @param source source
     * @return user attributes
     */
    public List<UserAttribute<?>> listBySource(UserAttribute.Source source) {
        return attributeMap.values().stream()
            .filter(attribute -> attribute.getSource() == source)
            .collect(Collectors.toList());
    }

    /**
     * Finds the first user attribute whose Keycloak source ID matches the given value.
     *
     * @param sourceId Keycloak attribute source ID
     * @return matching attribute or null if none found
     */
    public UserAttribute<?> findBySourceId(String sourceId) {
        if (sourceId == null) {
            return null;
        }
        return attributeMap.values().stream()
            .filter(attr -> sourceId.equals(attr.getSourceId()))
            .findFirst()
            .orElse(null);
    }

}

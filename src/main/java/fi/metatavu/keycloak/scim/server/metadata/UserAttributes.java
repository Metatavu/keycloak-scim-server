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

}

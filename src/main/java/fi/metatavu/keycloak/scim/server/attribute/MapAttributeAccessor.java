package fi.metatavu.keycloak.scim.server.attribute;

import java.util.Map;

public class MapAttributeAccessor implements ScimAttributeAccessor {

    private final Map<String, Object> values;

    public MapAttributeAccessor(Map<String, Object> values) {
        this.values = values;
    }

    @Override
    public Object read(String scimPath) {
        return values.get(scimPath);
    }
}
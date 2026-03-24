package fi.metatavu.keycloak.scim.server.filter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import fi.metatavu.keycloak.scim.server.attribute.MapAttributeAccessor;
import fi.metatavu.keycloak.scim.server.attribute.ScimAttributeAccessor;

public class ValuePathFilter implements ScimFilter {

    private final String attrPath;
    private final ScimFilter innerFilter;

    public ValuePathFilter(String attrPath, ScimFilter innerFilter) {
        this.attrPath = attrPath;
        this.innerFilter = innerFilter;
    }

    public String getAttrPath() {
        return attrPath;
    }

    public ScimFilter getInnerFilter() {
        return innerFilter;
    }

    @Override
    public boolean matches(ScimAttributeAccessor accessor) {
        Object value = accessor.read(attrPath);

        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedMap = (Map<String, Object>) map;

                    if (innerFilter.matches(new MapAttributeAccessor(typedMap))) {
                        return true;
                    }
                }
            }
            return false;
        }

        if ("emails".equals(attrPath) && value instanceof String email) {
            Map<String, Object> emailObj = new HashMap<>();
            emailObj.put("value", email);
            emailObj.put("primary", true);
            return innerFilter.matches(new MapAttributeAccessor(emailObj));
        }

        return false;
    }
}
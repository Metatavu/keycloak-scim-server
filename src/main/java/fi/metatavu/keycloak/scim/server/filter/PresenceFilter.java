package fi.metatavu.keycloak.scim.server.filter;

import fi.metatavu.keycloak.scim.server.attribute.ScimAttributeAccessor;

/**
 * Presence SCIM filter
 * <p>
 * This class is responsible for presence SCIM filters
 *
 * @param attribute attribute
 */
public class PresenceFilter implements ScimFilter {

    private final String attr;

    public PresenceFilter(String attr) {
        this.attr = attr;
    }

    @Override
    public boolean matches(ScimAttributeAccessor accessor) {
        Object value = accessor.read(attr);
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null;
    }

    public String getAttr() { return attr; }
}
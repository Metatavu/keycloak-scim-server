package fi.metatavu.keycloak.scim.server.filter;

import fi.metatavu.keycloak.scim.server.attribute.ScimAttributeAccessor;

/**
 * SCIM filter
 */
public interface ScimFilter {
    enum Operator { EQ, PR, AND, OR, CO, EW, SW }
    boolean matches(ScimAttributeAccessor accessor);
}


package fi.metatavu.keycloak.scim.server.filter;

/**
 * SCIM filter
 */
public interface ScimFilter {
    enum Operator { EQ, PR, AND, OR }
}


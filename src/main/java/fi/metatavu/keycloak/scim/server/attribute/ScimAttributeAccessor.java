package fi.metatavu.keycloak.scim.server.attribute;

public interface ScimAttributeAccessor {
    Object read(String scimPath);
}
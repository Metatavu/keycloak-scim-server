package fi.metatavu.keycloak.scim.server.attribute;

import org.keycloak.models.UserModel;

import fi.metatavu.keycloak.scim.server.metadata.UserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.users.UnsupportedUserPath;


public class UserAttributeAccessor implements ScimAttributeAccessor {

    private final UserModel user;
    private final UserAttributes userAttributes;

    public UserAttributeAccessor(UserModel user, UserAttributes userAttributes) {
        this.user = user;
        this.userAttributes = userAttributes;
    }

    @Override
    public Object read(String scimPath) {
        UserAttribute<?> attribute = userAttributes.findByScimPath(scimPath);
        if (attribute == null) {
            throw new UnsupportedUserPath("Unsupported attribute: " + scimPath);
        }
        return attribute.read(user);
    }
}
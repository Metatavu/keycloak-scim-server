package fi.metatavu.keycloak.scim.server.users;

import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import java.util.function.Predicate;

/**
 * Decides which Keycloak users the SCIM API is allowed to see and delete.
 *
 * By default only users carrying the scim-managed role (granted on SCIM create) are exposed.
 * When the manage-all-users setting is enabled for the realm or organization, every user is exposed.
 */
public final class ScimManagedUsers {

    private ScimManagedUsers() {
    }

    /**
     * Returns a predicate selecting the users that the SCIM user list should include
     *
     * @param scimContext SCIM context
     * @return predicate that is true for users visible through SCIM
     * @throws IllegalStateException if the scim-managed role is missing from the realm while it is required
     */
    public static Predicate<UserModel> predicate(ScimContext scimContext) {
        if (scimContext.getConfig().getManageAllUsers()) {
            return ScimManagedUsers::isNotServiceAccount;
        }

        RoleModel scimManagedRole = scimContext.getRealm().getRole(ScimRoles.SCIM_MANAGED_ROLE);
        if (scimManagedRole == null) {
            throw new IllegalStateException("SCIM managed role not found");
        }

        return user -> isNotServiceAccount(user) && user.hasRole(scimManagedRole);
    }

    /**
     * Returns whether the SCIM API may delete the given user.
     *
     * Fails closed: when the scim-managed role does not exist in the realm, no user is
     * considered managed unless the manage-all-users setting is enabled.
     *
     * @param scimContext SCIM context
     * @param user user
     * @return true if the user is managed through SCIM
     */
    public static boolean isManaged(ScimContext scimContext, UserModel user) {
        if (!isNotServiceAccount(user)) {
            return false;
        }

        if (scimContext.getConfig().getManageAllUsers()) {
            return true;
        }

        RoleModel scimManagedRole = scimContext.getRealm().getRole(ScimRoles.SCIM_MANAGED_ROLE);
        return scimManagedRole != null && user.hasRole(scimManagedRole);
    }

    /**
     * Service accounts belong to clients and must never be listed or deleted through SCIM
     */
    private static boolean isNotServiceAccount(UserModel user) {
        return user.getServiceAccountClientLink() == null;
    }
}

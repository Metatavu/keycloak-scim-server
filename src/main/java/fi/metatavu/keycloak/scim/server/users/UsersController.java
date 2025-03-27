package fi.metatavu.keycloak.scim.server.users;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.consts.Schemas;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.consts.UserAttribute;
import fi.metatavu.keycloak.scim.server.filter.ComparisonFilter;
import fi.metatavu.keycloak.scim.server.filter.LogicalFilter;
import fi.metatavu.keycloak.scim.server.filter.PresenceFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.model.UsersList;
import jakarta.ws.rs.NotFoundException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Users controller
 */
public class UsersController extends AbstractController {

    /**
     * Creates a user
     *
     * @param scimContext SCIM context
     * @param scimUser SCIM user
     * @return created user
     */
    public fi.metatavu.keycloak.scim.server.model.User createUser(
        ScimContext scimContext,
        fi.metatavu.keycloak.scim.server.model.User scimUser
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        UserModel user = session.users().addUser(realm, scimUser.getUserName());
        user.setEnabled(scimUser.getActive() == null || Boolean.TRUE.equals(scimUser.getActive()));

        if (scimUser.getName() != null) {
            user.setFirstName(scimUser.getName().getGivenName());
            user.setLastName(scimUser.getName().getFamilyName());
        }

        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            user.setEmail(scimUser.getEmails().getFirst().getValue());
        }

        RoleModel scimRole = realm.getRole(ScimRoles.SCIM_MANAGED_ROLE);
        if (scimRole != null) {
            user.grantRole(scimRole);
        }

        return translateUser(
            scimContext,
            user
        );
    }

    /**
     * Finds a user
     *
     * @param scimContext SCIM context
     * @param userId user ID
     * @return found user
     */
    public User findUser(
        ScimContext scimContext,
        String userId
    ) {
        try {
            KeycloakSession session = scimContext.getSession();
            RealmModel realm = scimContext.getRealm();
            UserModel userModel = session.users().getUserById(realm, userId);

            return translateUser(
                scimContext,
                userModel
            );
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * Lists users
     *
     * @param scimContext SCIM context
     * @param scimFilter SCIM filter
     * @param firstResult first result
     * @param maxResults max results
     * @return users list
     */
    public UsersList listUsers(
            ScimContext scimContext,
            ScimFilter scimFilter,
            Integer firstResult,
            Integer maxResults
    ) {
        UsersList result = new UsersList();
        RealmModel realm = scimContext.getRealm();
        KeycloakSession session = scimContext.getSession();

        Map<String, String> searchParams = new HashMap<>();

        if (scimFilter instanceof ComparisonFilter cmp) {
            if (cmp.operator() == ScimFilter.Operator.EQ) {
                UserAttribute userAttribute = UserAttribute.findByName(cmp.attribute());
                if (userAttribute == null) {
                    throw new UnsupportedUserFilter("Unsupported attribute: " + cmp.attribute());
                }

                String value = cmp.value();

                switch (userAttribute) {
                    case UserAttribute.USERNAME -> searchParams.put(UserModel.USERNAME, value);
                    case UserAttribute.EMAIL -> searchParams.put(UserModel.EMAIL, value);
                    case UserAttribute.GIVEN_NAME -> searchParams.put(UserModel.FIRST_NAME, value);
                    case UserAttribute.FAMILY_NAME -> searchParams.put(UserModel.LAST_NAME, value);
                    case UserAttribute.ACTIVE -> searchParams.put(UserModel.ENABLED, value);
                }
            }
        }

        RoleModel scimManagedRole = realm.getRole(ScimRoles.SCIM_MANAGED_ROLE);
        if (scimManagedRole == null) {
            throw new IllegalStateException("SCIM managed role not found");
        }

        List<UserModel> filteredUsers = session.users()
            .searchForUserStream(scimContext.getRealm(), searchParams)
            .filter(user -> !searchParams.isEmpty() || matchScimFilter(user, scimFilter))
            .filter(user -> user.hasRole(scimManagedRole))
            .toList();

        List<User> users = filteredUsers.stream()
            .skip(firstResult)
            .limit(maxResults)
            .map(user -> translateUser(scimContext, user))
            .toList();

        result.setTotalResults(filteredUsers.size());
        result.setResources(users);
        result.setStartIndex(firstResult);
        result.setItemsPerPage(maxResults);

        return result;
    }

    /**
     * Tests if user matches SCIM filter
     *
     * @param user user
     * @param filter SCIM filter
     * @return true if user matches filter
     */
    private boolean matchScimFilter(UserModel user, ScimFilter filter) {
        switch (filter) {
            case null -> {
                return true;
            }
            case ComparisonFilter cmp -> {
                UserAttribute attr = UserAttribute.findByName(cmp.attribute());
                if (attr == null) {
                    throw new UnsupportedUserFilter("Unsupported attribute: " + cmp.attribute());
                }

                String value = cmp.value();
                String actual = switch (attr) {
                    case UserAttribute.USERNAME -> user.getUsername();
                    case UserAttribute.EMAIL -> user.getEmail();
                    case UserAttribute.GIVEN_NAME -> user.getFirstName();
                    case UserAttribute.FAMILY_NAME -> user.getLastName();
                    case UserAttribute.ACTIVE -> Boolean.toString(user.isEnabled());
                };

                if (actual == null) return false;

                return switch (cmp.operator()) {
                    case EQ -> actual.equalsIgnoreCase(value);
                    case CO -> actual.toLowerCase().contains(value.toLowerCase());
                    case SW -> actual.toLowerCase().startsWith(value.toLowerCase());
                    case EW -> actual.toLowerCase().endsWith(value.toLowerCase());
                    default -> false;
                };
            }
            case LogicalFilter logical -> {
                boolean left = matchScimFilter(user, logical.left());
                boolean right = matchScimFilter(user, logical.right());

                return switch (logical.operator()) {
                    case AND -> left && right;
                    case OR -> left || right;
                    default -> false;
                };
            }
            case PresenceFilter presence -> {
                UserAttribute presenceAttribute = UserAttribute.findByName(presence.attribute());
                if (presenceAttribute == null) {
                    throw new UnsupportedUserFilter("Unsupported attribute: " + presence.attribute());
                }

                return switch (presenceAttribute) {
                    case UserAttribute.USERNAME -> user.getUsername() != null;
                    case UserAttribute.EMAIL -> user.getEmail() != null;
                    case UserAttribute.GIVEN_NAME -> user.getFirstName() != null;
                    case UserAttribute.FAMILY_NAME -> user.getLastName() != null;
                    case UserAttribute.ACTIVE -> true;
                };
            }
            default -> {
            }
        }

        return false;
    }

    /**
     * Translates Keycloak user to SCIM user
     *
     * @param user Keycloak user
     * @return SCIM user
     */
    private fi.metatavu.keycloak.scim.server.model.User translateUser(
        ScimContext scimContext,
        UserModel user
    ) {
        if (user == null) {
            return null;
        }

        return new fi.metatavu.keycloak.scim.server.model.User()
            .id(user.getId())
            .userName(user.getUsername())
            .active(user.isEnabled())
            .emails(Collections.singletonList(new fi.metatavu.keycloak.scim.server.model.UserEmailsInner()
                    .value(user.getEmail())
                    .primary(true)
            ))
            .meta(getMeta(scimContext, "User", String.format("Users/%s", user.getId())))
            .schemas(Collections.singletonList(Schemas.USER_SCHEMA))
            .name(new fi.metatavu.keycloak.scim.server.model.UserName()
                    .familyName(user.getLastName())
                    .givenName(user.getFirstName())
            );
    }

    /**
     * Updates a user with SCIM user data
     *
     * @param scimContext SCIM context
     * @param existing existing user
     * @param scimUser SCIM user
     * @return updated user
     */
    public fi.metatavu.keycloak.scim.server.model.User updateUser(
        ScimContext scimContext,
        UserModel existing,
        User scimUser
    ) {
        existing.setUsername(scimUser.getUserName());
        existing.setEnabled(scimUser.getActive() == null || Boolean.TRUE.equals(scimUser.getActive()));

        if (scimUser.getName() != null) {
            existing.setFirstName(scimUser.getName().getGivenName());
            existing.setLastName(scimUser.getName().getFamilyName());
        }

        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            existing.setEmail(scimUser.getEmails().getFirst().getValue());
        }

        return translateUser(scimContext, existing);
    }
}

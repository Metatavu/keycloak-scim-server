package fi.metatavu.keycloak.scim.server.users;

import fi.metatavu.keycloak.scim.server.consts.FilterAttributes;
import fi.metatavu.keycloak.scim.server.consts.Schemas;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.filter.ComparisonFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.model.UsersList;
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
public class UsersController {

    /**
     * Creates a user
     *
     * @param session Keycloak session
     * @param realm Keycloak realm
     * @param scimUser SCIM user
     * @return created user
     */
    public fi.metatavu.keycloak.scim.server.model.User createUser(KeycloakSession session, RealmModel realm, fi.metatavu.keycloak.scim.server.model.User scimUser) {
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

        return translateUser(user);
    }

    /**
     * Lists users
     *
     * @param realm realm
     * @param session session
     * @param scimFilter SCIM filter
     * @param firstResult first result
     * @param maxResults max results
     * @return users list
     */
    public UsersList listUsers(
            RealmModel realm,
            KeycloakSession session,
            ScimFilter scimFilter,
            Integer firstResult,
            Integer maxResults
    ) {
        UsersList result = new UsersList();

        Map<String, String> searchParams = new HashMap<>();

        if (scimFilter instanceof ComparisonFilter cmp) {
            if (cmp.operator() == ScimFilter.Operator.EQ) {
                String attr = cmp.attribute();
                String value = cmp.value();

                switch (attr) {
                    case FilterAttributes.USERNAME -> searchParams.put(UserModel.USERNAME, value);
                    case FilterAttributes.EMAIL -> searchParams.put(UserModel.EMAIL, value);
                    case FilterAttributes.FIRST_NAME -> searchParams.put(UserModel.FIRST_NAME, value);
                    case FilterAttributes.LAST_NAME, FilterAttributes.FAMILY_NAME -> searchParams.put(UserModel.LAST_NAME, value);
                    case FilterAttributes.ACTIVE -> searchParams.put(UserModel.ENABLED, value);
                    default -> throw new UnsupportedUserFilter("Unsupported equals comparison filter: " + attr);
                }
            } else {
               throw new UnsupportedUserFilter("Unsupported operator: " + cmp.operator());
            }
        }

        RoleModel scimManagedRole = realm.getRole(ScimRoles.SCIM_MANAGED_ROLE);
        if (scimManagedRole == null) {
            throw new IllegalStateException("SCIM managed role not found");
        }

        List<UserModel> filteredUsers = session.users()
            .searchForUserStream(realm, searchParams)
            .filter(user -> user.hasRole(scimManagedRole))
            .toList();

        List<User> users = filteredUsers.stream()
            .limit(maxResults)
            .skip(firstResult)
            .map(this::translateUser)
            .toList();

        result.setTotalResults(filteredUsers.size());
        result.setResources(users);
        result.setStartIndex(firstResult);
        result.setItemsPerPage(maxResults);

        return result;
    }

    /**
     * Translates Keycloak user to SCIM user
     *
     * @param user Keycloak user
     * @return SCIM user
     */
    private fi.metatavu.keycloak.scim.server.model.User translateUser(UserModel user) {
        return new fi.metatavu.keycloak.scim.server.model.User()
            .id(user.getId())
            .userName(user.getUsername())
            .active(user.isEnabled())
            .emails(Collections.singletonList(new fi.metatavu.keycloak.scim.server.model.UserEmailsInner()
                    .value(user.getEmail())
                    .primary(true)
            ))
            .schemas(Collections.singletonList(Schemas.USER_SCHEMA))
            .name(new fi.metatavu.keycloak.scim.server.model.UserName()
                    .familyName(user.getLastName())
                    .givenName(user.getFirstName())
            );
    }

}

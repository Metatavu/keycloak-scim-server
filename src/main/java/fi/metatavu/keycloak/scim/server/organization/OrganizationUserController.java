package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.filter.ComparisonFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.metadata.BooleanUserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.StringUserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.users.UnsupportedUserPath;
import fi.metatavu.keycloak.scim.server.users.UsersController;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.organization.OrganizationProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrganizationUserController extends UsersController  {

    private static final Logger logger = Logger.getLogger(OrganizationUserController.class);

    /**
     * Creates a user
     *
     * @param scimContext SCIM context
     * @param scimUser SCIM user
     * @return created user
     */
    public fi.metatavu.keycloak.scim.server.model.User createOrganizationUser(
        OrganizationScimContext scimContext,
        UserAttributes userAttributes,
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
            user.setEmailVerified(true);
        }

        RoleModel scimRole = realm.getRole(ScimRoles.SCIM_MANAGED_ROLE);
        if (scimRole != null) {
            user.grantRole(scimRole);
        }

        Map<String, Object> additionalProperties = scimUser.getAdditionalProperties();
        if (additionalProperties != null) {
            additionalProperties.forEach((key, value) -> {
                UserAttribute<?> userAttribute = userAttributes.findByScimPath(key);
                if (userAttribute != null) {
                    if (userAttribute instanceof StringUserAttribute) {
                        if (value instanceof String) {
                            ((StringUserAttribute) userAttribute).write(user, (String) value);
                        } else {
                            logger.warn("Unsupported value type: " + value.getClass());
                        }
                    } else if (userAttribute instanceof BooleanUserAttribute) {
                        if (value instanceof Boolean) {
                            ((BooleanUserAttribute) userAttribute).write(user, (Boolean) value);
                        } else {
                            logger.warn("Unsupported value type: " + value.getClass());
                        }
                    } else {
                        logger.warn("Unsupported attribute: " + key);
                    }
                }
            });
        }

        OrganizationProvider organizationProvider = getOrganizationProvider(scimContext.getSession());
        organizationProvider.addManagedMember(scimContext.getOrganization(), user);

        return translateUser(
            scimContext,
            userAttributes,
            user
        );
    }

    /**
     * Finds a user
     *
     * @param scimContext SCIM context
     * @param userAttributes user attributes
     * @param userId user ID
     * @return found user
     */
    public fi.metatavu.keycloak.scim.server.model.User findOrganizationUser(
        OrganizationScimContext scimContext,
        UserAttributes userAttributes,
        String userId
    ) {
        try {
            UserModel organizationUser = getOrganizationProvider(scimContext.getSession()).getMemberById(
                scimContext.getOrganization(),
                userId
            );

            return translateUser(
                scimContext,
                userAttributes,
                organizationUser
            );
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * Lists users from the organization
     *
     * @param scimContext SCIM context
     * @param scimFilter SCIM filter
     * @param firstResult first result
     * @param maxResults max results
     * @return users list
     */
    public fi.metatavu.keycloak.scim.server.model.UsersList listOrganizationUsers(
        OrganizationScimContext scimContext,
        ScimFilter scimFilter,
        UserAttributes userAttributes,
        Integer firstResult,
        Integer maxResults
    ) {
        fi.metatavu.keycloak.scim.server.model.UsersList result = new fi.metatavu.keycloak.scim.server.model.UsersList();
        RealmModel realm = scimContext.getRealm();
        KeycloakSession session = scimContext.getSession();

        Map<String, String> searchParams = new HashMap<>();

        if (scimFilter instanceof ComparisonFilter cmp) {
            if (cmp.operator() == ScimFilter.Operator.EQ) {
                UserAttribute<?> userAttribute = userAttributes.findByScimPath(cmp.attribute());
                if (userAttribute == null) {
                    throw new UnsupportedUserPath("Unsupported attribute: " + cmp.attribute());
                }

                String value = cmp.value();

                if (userAttribute.getSource() == UserAttribute.Source.USER_MODEL || userAttribute.getSource() == UserAttribute.Source.USER_PROFILE) {
                    searchParams.put(userAttribute.getSourceId(), value);
                }
            }
        }

        RoleModel scimManagedRole = realm.getRole(ScimRoles.SCIM_MANAGED_ROLE);
        if (scimManagedRole == null) {
            throw new IllegalStateException("SCIM managed role not found");
        }

        List<UserModel> filteredUsers = getOrganizationProvider(session).getMembersStream(scimContext.getOrganization(), searchParams, true, null, null)
            .filter(user -> user.hasRole(scimManagedRole))
            .filter(user -> !searchParams.isEmpty() || matchScimFilter(user, userAttributes, scimFilter))
            .filter(user -> user.hasRole(scimManagedRole))
            .toList();

        List<fi.metatavu.keycloak.scim.server.model.User> users = filteredUsers.stream()
            .skip(firstResult)
            .limit(maxResults)
            .map(user -> translateUser(scimContext, userAttributes, user))
            .toList();

        result.setTotalResults(filteredUsers.size());
        result.setResources(users);
        result.setStartIndex(firstResult);
        result.setItemsPerPage(maxResults);

        return result;
    }

    /**
     * Deletes a user from the organization
     *
     * @param scimContext SCIM context
     * @param user user to delete
     */
    public void deleteOrganizationUser(OrganizationScimContext scimContext, UserModel user) {
        KeycloakSession session = scimContext.getSession();
        OrganizationProvider organizationProvider = getOrganizationProvider(session);

        // TODO: Should we also remove the whole user?

        if (organizationProvider.isManagedMember(scimContext.getOrganization(), user)) {
            organizationProvider.removeMember(scimContext.getOrganization(), user);
        } else {
            throw new NotFoundException("User is not a member of the organization");
        }
    }

    /**
     * Returns the organization provider
     *
     * @param session Keycloak session
     * @return Organization provider
     */
    private OrganizationProvider getOrganizationProvider(KeycloakSession session) {
        KeycloakContext context = session.getContext();
        if (context == null) {
            throw new IllegalStateException("Keycloak context is not set");
        }

        return session.getProvider(OrganizationProvider.class);
    }
}

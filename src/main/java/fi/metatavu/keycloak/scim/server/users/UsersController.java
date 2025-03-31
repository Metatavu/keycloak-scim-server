package fi.metatavu.keycloak.scim.server.users;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.consts.Schemas;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.filter.ComparisonFilter;
import fi.metatavu.keycloak.scim.server.filter.LogicalFilter;
import fi.metatavu.keycloak.scim.server.filter.PresenceFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.metadata.BooleanUserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.StringUserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.model.UsersList;
import fi.metatavu.keycloak.scim.server.patch.PatchOperation;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import java.util.*;

/**
 * Users controller
 */
public class UsersController extends AbstractController {

    private static final Logger logger = Logger.getLogger(UsersController.class);

    /**
     * Creates a user
     *
     * @param scimContext SCIM context
     * @param scimUser SCIM user
     * @return created user
     */
    public fi.metatavu.keycloak.scim.server.model.User createUser(
        ScimContext scimContext,
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

        if (scimUser.getDisplayName() != null) {
            UserAttribute<?> userAttribute = userAttributes.findByScimPath("displayName");
            if (userAttribute instanceof StringUserAttribute) {
                ((StringUserAttribute) userAttribute).write(user, scimUser.getDisplayName());
            } else {
                logger.warn("Unsupported attribute displayName");
            }
        }

        if (scimUser.getExternalId() != null) {
            UserAttribute<?> userAttribute = userAttributes.findByScimPath("externalId");
            if (userAttribute instanceof StringUserAttribute) {
                ((StringUserAttribute) userAttribute).write(user, scimUser.getExternalId());
            } else {
                logger.warn("Unsupported attribute externalId");
            }
        }

        if (scimUser.getPreferredLanguage() != null) {
            UserAttribute<?> userAttribute = userAttributes.findByScimPath("preferredLanguage");
            if (userAttribute instanceof StringUserAttribute) {
                ((StringUserAttribute) userAttribute).write(user, scimUser.getPreferredLanguage());
            } else {
                logger.warn("Unsupported attribute preferredLanguage");
            }
        }

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
    public User findUser(
        ScimContext scimContext,
        UserAttributes userAttributes,
        String userId
    ) {
        try {
            KeycloakSession session = scimContext.getSession();
            RealmModel realm = scimContext.getRealm();
            UserModel userModel = session.users().getUserById(realm, userId);

            return translateUser(
                scimContext,
                userAttributes,
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
        UserAttributes userAttributes,
        Integer firstResult,
        Integer maxResults
    ) {
        UsersList result = new UsersList();
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

        List<UserModel> filteredUsers = session.users()
            .searchForUserStream(scimContext.getRealm(), searchParams)
            .filter(user -> !searchParams.isEmpty() || matchScimFilter(user, userAttributes, scimFilter))
            .filter(user -> user.hasRole(scimManagedRole))
            .toList();

        List<User> users = filteredUsers.stream()
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
     * Tests if user matches SCIM filter
     *
     * @param user user
     * @param userAttributes user attributes
     * @param filter SCIM filter
     * @return true if user matches filter
     */
    private boolean matchScimFilter(
        UserModel user,
        UserAttributes userAttributes,
        ScimFilter filter
    ) {
        switch (filter) {
            case null -> {
                return true;
            }
            case ComparisonFilter cmp -> {
                UserAttribute<?> userAttribute = userAttributes.findByScimPath(cmp.attribute());
                if (userAttribute == null) {
                    throw new UnsupportedUserPath("Unsupported attribute: " + cmp.attribute());
                }

                String value = cmp.value();
                Object actual = userAttribute.read(user);
                if (actual == null) return false;

                String actualString;
                if (actual instanceof String) {
                    actualString = (String) actual;
                } else if (actual instanceof Boolean) {
                    actualString = Boolean.toString((Boolean) actual);
                } else {
                    throw new UnsupportedUserPath("Unsupported attribute type: " + actual.getClass());
                }

                return switch (cmp.operator()) {
                    case EQ -> actualString.equalsIgnoreCase(value);
                    case CO -> actualString.toLowerCase().contains(value.toLowerCase());
                    case SW -> actualString.toLowerCase().startsWith(value.toLowerCase());
                    case EW -> actualString.toLowerCase().endsWith(value.toLowerCase());
                    default -> false;
                };
            }
            case LogicalFilter logical -> {
                boolean left = matchScimFilter(user, userAttributes, logical.left());
                boolean right = matchScimFilter(user, userAttributes, logical.right());

                return switch (logical.operator()) {
                    case AND -> left && right;
                    case OR -> left || right;
                    default -> false;
                };
            }
            case PresenceFilter presence -> {
                UserAttribute<?> presenceAttribute = userAttributes.findByScimPath(presence.attribute());
                if (presenceAttribute == null) {
                    throw new UnsupportedUserPath("Unsupported attribute: " + presence.attribute());
                }

                Object value = presenceAttribute.read(user);
                if (value instanceof Boolean) {
                    return (Boolean) value;
                }

                return value != null;
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
        UserAttributes userAttributes,
        UserModel user
    ) {
        if (user == null) {
            return null;
        }

        return new fi.metatavu.keycloak.scim.server.model.User()
            .id(user.getId())
            .userName(user.getUsername())
            .active(user.isEnabled())
            .externalId(readStringUserAttribute(userAttributes, "externalId", user))
            .preferredLanguage(readStringUserAttribute(userAttributes, "preferredLanguage", user))
            .displayName(readStringUserAttribute(userAttributes, "displayName", user))
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
     * @param userAttributes user attributes
     * @param existing existing user
     * @param scimUser SCIM user
     * @return updated user
     */
    public fi.metatavu.keycloak.scim.server.model.User updateUser(
        ScimContext scimContext,
        UserAttributes userAttributes,
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

        if (scimUser.getDisplayName() != null) {
            UserAttribute<?> userAttribute = userAttributes.findByScimPath("displayName");
            if (userAttribute instanceof StringUserAttribute) {
                ((StringUserAttribute) userAttribute).write(existing, scimUser.getDisplayName());
            } else {
                logger.warn("Unsupported attribute displayName");
            }
        }

        if (scimUser.getExternalId() != null) {
            UserAttribute<?> userAttribute = userAttributes.findByScimPath("externalId");
            if (userAttribute instanceof StringUserAttribute) {
                ((StringUserAttribute) userAttribute).write(existing, scimUser.getExternalId());
            } else {
                logger.warn("Unsupported attribute externalId");
            }
        }

        if (scimUser.getPreferredLanguage() != null) {
            UserAttribute<?> userAttribute = userAttributes.findByScimPath("preferredLanguage");
            if (userAttribute instanceof StringUserAttribute) {
                ((StringUserAttribute) userAttribute).write(existing, scimUser.getPreferredLanguage());
            } else {
                logger.warn("Unsupported attribute preferredLanguage");
            }
        }

        return translateUser(scimContext, userAttributes, existing);
    }

    /**
     * Patch user with SCIM user data
     *
     * @param scimContext SCIM context
     * @param userAttributes user attributes
     * @param existing existing user
     * @param patchRequest patch request
     * @return patched user
     */
    public fi.metatavu.keycloak.scim.server.model.User patchUser(
        ScimContext scimContext,
        UserAttributes userAttributes,
        UserModel existing,
        fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest
    ) throws UnsupportedPatchOperation {
        for (var operation : patchRequest.getOperations()) {
            PatchOperation op = PatchOperation.fromString(operation.getOp());
            if (op == null) {
                logger.warn("Invalid patch operation: " + operation.getOp());
                throw new UnsupportedPatchOperation("Unsupported patch operation: " + operation.getOp());
            }

            UserAttribute<?> userAttribute = userAttributes.findByScimPath(operation.getPath());
            Object value = operation.getValue();

            if (userAttribute == null) {
                throw new UnsupportedUserPath("Unsupported attribute: " + operation.getPath());
            }

            switch (op) {
                case REPLACE, ADD -> {
                    switch (value) {
                        case null:
                            logger.warn("Value is null for patch operation: " + op);
                        break;
                        case String s when userAttribute instanceof StringUserAttribute:
                            ((StringUserAttribute) userAttribute).write(existing, s);
                        break;
                        case Boolean b when userAttribute instanceof BooleanUserAttribute:
                            ((BooleanUserAttribute) userAttribute).write(existing, b);
                        break;
                        default:
                            logger.warn("Unsupported value type for patch operation: " + value.getClass());
                        break;
                    }

                }
                case REMOVE -> userAttribute.write(existing, null);
            }
        }

        return translateUser(scimContext, userAttributes, existing);
    }

    /**
     * Reads a user attribute
     *
     * @param userAttributes user attributes
     * @param attributeName attribute name
     * @param user user
     * @return attribute value
     */
    private String readStringUserAttribute(UserAttributes userAttributes, String attributeName, UserModel user) {
        UserAttribute<?> userAttribute = userAttributes.findByScimPath(attributeName);
        if (userAttribute == null) {
            return null;
        }

        Object value = userAttribute.read(user);
        if (value instanceof String) {
            return (String) value;
        }

        return null;
    }

}

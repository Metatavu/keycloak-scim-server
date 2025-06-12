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
import fi.metatavu.keycloak.scim.server.realm.RealmScimContext;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.*;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
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

        User createdUser = translateUser(
            scimContext,
            userAttributes,
            user
        );

        dispatchUserCreateEvent(
            scimContext,
            user
        );

        return createdUser;
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
        ((StringUserAttribute) userAttributes.findByScimPath("userName")).write(existing, scimUser.getUserName());
        ((BooleanUserAttribute) userAttributes.findByScimPath("active")).write(existing, scimUser.getActive() == null || Boolean.TRUE.equals(scimUser.getActive()));

        if (scimUser.getName() != null) {
            ((StringUserAttribute) userAttributes.findByScimPath("name.givenName")).write(existing, scimUser.getName().getGivenName());
            ((StringUserAttribute) userAttributes.findByScimPath("name.familyName")).write(existing, scimUser.getName().getFamilyName());
        }

        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            ((StringUserAttribute) userAttributes.findByScimPath("email")).write(existing, scimUser.getEmails().getFirst().getValue());
        }

        Map<String, Object> additionalProperties = scimUser.getAdditionalProperties();
        if (additionalProperties != null) {
            additionalProperties.forEach((key, value) -> {
                UserAttribute<?> userAttribute = userAttributes.findByScimPath(key);
                if (userAttribute != null) {
                    if (userAttribute instanceof StringUserAttribute) {
                        if (value instanceof String) {
                            ((StringUserAttribute) userAttribute).write(existing, (String) value);
                        } else {
                            logger.warn("Unsupported value type: " + value.getClass());
                        }
                    } else if (userAttribute instanceof BooleanUserAttribute) {
                        if (value instanceof Boolean) {
                            ((BooleanUserAttribute) userAttribute).write(existing, (Boolean) value);
                        } else {
                            logger.warn("Unsupported value type: " + value.getClass());
                        }
                    } else {
                        logger.warn("Unsupported attribute: " + key);
                    }
                }
            });
        }

        dispatchUserUpdateEvent(scimContext, existing);

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

        dispatchUserUpdateEvent(scimContext, existing);

        return translateUser(scimContext, userAttributes, existing);
    }

    /**
     * Dispatches user create event
     *
     * @param scimContext SCIM context
     * @param user user
     */
    protected void dispatchUserCreateEvent(
        ScimContext scimContext,
        UserModel user
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();
        UserRepresentation userRepresentation = ModelToRepresentation.toRepresentation(session, realm, user);

        sendAdminEvent(
            scimContext,
            OperationType.CREATE,
            ResourceType.USER,
            "users/" + user.getId(),
            userRepresentation
        );
    }

    /**
     * Dispatches user creation event
     *
     * @param scimContext SCIM context
     * @param user user
     */
    protected void dispatchUserDeleteEvent(
        ScimContext scimContext,
        UserModel user
    ) {
        sendAdminEvent(
            scimContext,
            OperationType.DELETE,
            ResourceType.USER,
            "users/" + user.getId(),
            null
        );
    }

    /**
     * Dispatches user update event
     *
     * @param scimContext SCIM context
     * @param user user
     */
    protected void dispatchUserUpdateEvent(
            ScimContext scimContext,
            UserModel user
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();
        UserRepresentation userRepresentation = ModelToRepresentation.toRepresentation(session, realm, user);

        sendAdminEvent(
            scimContext,
            OperationType.UPDATE,
            ResourceType.USER,
            "users/" + user.getId(),
            userRepresentation
        );
    }

    /**
     * Sends an admin event
     *
     * @param scimContext SCIM context
     * @param operationType operation type
     * @param resourceType resource type
     * @param resourcePath resource path
     * @param representation representation
     */
    @SuppressWarnings("SameParameterValue")
    protected void sendAdminEvent(
        ScimContext scimContext,
        OperationType operationType,
        ResourceType resourceType,
        String resourcePath,
        Object representation
    ) {
        sendAdminEvent(scimContext, operationType, resourceType, resourcePath, representation, null);
    }

    /**
     * Sends an admin event with additional details
     *
     * @param scimContext SCIM context
     * @param operationType operation type
     * @param resourceType resource type
     * @param resourcePath resource path
     * @param representation representation
     * @param details additional details
     */
    protected void sendAdminEvent(
        ScimContext scimContext,
        OperationType operationType,
        ResourceType resourceType,
        String resourcePath,
        Object representation,
        Map<String, String> details
    ) {
        RealmModel realm = scimContext.getRealm();

        if (!realm.isAdminEventsEnabled()) {
            return;
        }

        KeycloakSession session = scimContext.getSession();

        boolean includeRepresentation = realm.isAdminEventsDetailsEnabled();

        AdminEvent event = new AdminEvent();
        event.setId(UUID.randomUUID().toString());
        event.setRealmId(realm.getId());
        event.setRealmName(realm.getName());
        event.setOperationType(operationType);
        event.setResourceType(resourceType);
        event.setResourcePath(resourcePath);
        event.setTime(System.currentTimeMillis());
        event.setDetails(details);

        if (representation != null) {
            try {
                event.setRepresentation(JsonSerialization.writeValueAsString(representation));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        List<String> realmListenerIds = realm.getEventsListenersStream().toList();

        session.getKeycloakSessionFactory()
            .getProviderFactoriesStream(EventListenerProvider.class)
            .filter(providerFactory -> realmListenerIds.contains(providerFactory.getId()) || ((EventListenerProviderFactory) providerFactory).isGlobal())
            .map(providerFactory -> providerFactory.create(session))
            .forEach(provider -> {
                if (provider instanceof EventListenerProvider eventListenerProvider) {
                    eventListenerProvider.onEvent(event, includeRepresentation);
                }
            });
    }

    /**
     * Tests if user matches SCIM filter
     *
     * @param user user
     * @param userAttributes user attributes
     * @param filter SCIM filter
     * @return true if user matches filter
     */
    protected boolean matchScimFilter(
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
    protected fi.metatavu.keycloak.scim.server.model.User translateUser(
            ScimContext scimContext,
            UserAttributes userAttributes,
            UserModel user
    ) {
        if (user == null) {
            return null;
        }

        boolean emailAsUsername = scimContext.getConfig().getEmailAsUsername();

        fi.metatavu.keycloak.scim.server.model.User result = new fi.metatavu.keycloak.scim.server.model.User()
                .id(user.getId())
                .userName(emailAsUsername ? user.getEmail() : user.getUsername())
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

        List<UserAttribute<?>> customAttributes = userAttributes.listBySource(UserAttribute.Source.USER_PROFILE);
        for (UserAttribute<?> userAttribute : customAttributes) {
            Object value = userAttribute.read(user);
            if (value != null) {
                result.putAdditionalProperty(userAttribute.getScimPath(), value);
            }
        }

        return result;
    }

    /**
     * Deletes a user
     *
     * @param scimContext SCIM context
     * @param user user
     */
    public void deleteUser(
        RealmScimContext scimContext,
        UserModel user
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();
        session.users().removeUser(realm, user);
        dispatchUserDeleteEvent(scimContext, user);
    }

    /**
     * Returns the email domain from the email address
     *
     * @param email email address
     * @return email domain
     */
    protected String getEmailDomain(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf('@') + 1);
        }

        return null;
    }
}

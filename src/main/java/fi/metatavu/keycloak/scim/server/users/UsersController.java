package fi.metatavu.keycloak.scim.server.users;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.adminEvents.AdminEventController;
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
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.FederatedIdentityModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.IdentityProviderStorageProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.*;

/**
 * Users controller
 */
public class UsersController extends AbstractController {

    private static final Logger logger = Logger.getLogger(UsersController.class);
    private final AdminEventController adminEventController = new AdminEventController();

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
    ) throws UserProfileValidationException {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        UserProfileValidationService.validateForCreate(session, userAttributes, scimUser);

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

        if (scimContext.getConfig().getLinkIdp()) {
            String scimUsername = createdUser.getUserName();
            String externalId = getExternalId(createdUser);
            String idpAlias = scimContext.getConfig().getIdentityProviderAlias();
            linkUserIdp(session, realm, user, scimUsername, externalId, idpAlias);
        }


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
    ) throws UserProfileValidationException {
        UserProfileValidationService.validateForUpdate(scimContext.getSession(), userAttributes, existing, scimUser);

        ((StringUserAttribute) userAttributes.findByScimPath("userName")).write(existing, scimUser.getUserName());
        ((BooleanUserAttribute) userAttributes.findByScimPath("active")).write(existing, scimUser.getActive() == null || Boolean.TRUE.equals(scimUser.getActive()));

        if (scimUser.getName() != null) {
            if (scimUser.getName().getGivenName() != null) {
                ((StringUserAttribute) userAttributes.findByScimPath("name.givenName")).write(existing, scimUser.getName().getGivenName());
            }
            if (scimUser.getName().getFamilyName() != null) {
                ((StringUserAttribute) userAttributes.findByScimPath("name.familyName")).write(existing, scimUser.getName().getFamilyName());
            }
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

        final User updatedUser = translateUser(scimContext, userAttributes, existing);

        if (scimContext.getConfig().getLinkIdp()) {
            KeycloakSession session = scimContext.getSession();
            RealmModel realm = scimContext.getRealm();
            String scimUsername = updatedUser.getUserName();
            String externalId = getExternalId(updatedUser);
            String idpAlias = scimContext.getConfig().getIdentityProviderAlias();
            linkUserIdp(session, realm, existing, scimUsername, externalId, idpAlias);
        }

        dispatchUserUpdateEvent(scimContext, existing);

        return updatedUser;
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
    ) throws UnsupportedPatchOperation, UserProfileValidationException {
        UserProfileValidationService.validateForPatch(
            scimContext.getSession(),
            userAttributes,
            existing,
            collectPatchAttributesForValidation(userAttributes, patchRequest)
        );

        applyPatchOperations(userAttributes, existing, patchRequest);

        dispatchUserUpdateEvent(scimContext, existing);

        final User patchedUser = translateUser(scimContext, userAttributes, existing);

        if (scimContext.getConfig().getLinkIdp()) {
            KeycloakSession session = scimContext.getSession();
            RealmModel realm = scimContext.getRealm();
            String scimUsername = patchedUser.getUserName();
            String externalId = getExternalId(patchedUser);
            String idpAlias = scimContext.getConfig().getIdentityProviderAlias();
            linkUserIdp(session, realm, existing, scimUsername, externalId, idpAlias);
        }


        return patchedUser;
    }

    /**
     * Collects attributes affected by a PATCH request in Keycloak user profile format.
     *
     * @param userAttributes user attributes metadata
     * @param patchRequest SCIM patch request
     * @return patched attributes keyed by Keycloak user profile attribute name
     * @throws UnsupportedPatchOperation when operation is unsupported
     */
    protected Map<String, Object> collectPatchAttributesForValidation(
        UserAttributes userAttributes,
        fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest
    ) throws UnsupportedPatchOperation {
        Map<String, Object> result = new HashMap<>();

        for (var operation : patchRequest.getOperations()) {
            PatchOperation op = PatchOperation.fromString(operation.getOp());
            if (op == null) {
                logger.warn("Invalid patch operation: " + operation.getOp());
                throw new UnsupportedPatchOperation("Unsupported patch operation: " + operation.getOp());
            }

            String path = operation.getPath();
            Object value = operation.getValue();

            if (path == null) {
                if (!(value instanceof Map<?, ?> valueMap)) {
                    throw new UnsupportedUserPath("PatchOp without 'path' requires a map-valued 'value'");
                }

                for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
                    String attrPath = String.valueOf(entry.getKey());
                    collectPatchAttributeForValidation(result, userAttributes, op, attrPath, entry.getValue());
                }
                continue;
            }

            collectPatchAttributeForValidation(result, userAttributes, op, path, value);
        }

        return result;
    }

    private void collectPatchAttributeForValidation(
        Map<String, Object> target,
        UserAttributes userAttributes,
        PatchOperation op,
        String path,
        Object value
    ) {
        if (isReadOnlyOrStructural(path)) {
            return;
        }

        UserAttribute<?> userAttribute = userAttributes.findByScimPath(path);
        if (userAttribute == null) {
            throw new UnsupportedUserPath("Unsupported attribute: " + path);
        }

        Object validationValue = op == PatchOperation.REMOVE ? null : UserProfileValidationService.normalizeValue(value);
        target.put(userAttribute.getSourceId(), validationValue);
    }

    /**
     * Walk a PatchRequest's operations and apply each one to {@code existing}.
     * Shared between {@link #patchUser} and
     * {@link fi.metatavu.keycloak.scim.server.organization.OrganizationUserController#patchOrganizationUser}
     * so the realm-scope and org-scope SCIM PATCH endpoints handle path-less /
     * path-based shapes and read-only / structural attributes identically.
     *
     * @param userAttributes user attributes metadata
     * @param existing       user being patched
     * @param patchRequest   SCIM patch request
     */
    protected void applyPatchOperations(
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

            String path = operation.getPath();
            Object value = operation.getValue();

            // RFC 7644 §3.5.2: when "path" is omitted, "value" carries a map of
            // attribute -> value to apply to the resource. Okta's Deactivate User
            // emits this shape: {"op":"replace","value":{"active":false}}.
            if (path == null) {
                if (!(value instanceof Map<?, ?> valueMap)) {
                    throw new UnsupportedUserPath("PatchOp without 'path' requires a map-valued 'value'");
                }
                for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
                    String attrPath = String.valueOf(entry.getKey());
                    if (isReadOnlyOrStructural(attrPath)) {
                        // RFC 7644 §3.5.2 / §7.5: ignore read-only and
                        // structural attributes (id, meta, schemas)
                        // on PATCH. Clients (Okta) echo them back from a prior GET.
                        continue;
                    }
                    UserAttribute<?> ua = userAttributes.findByScimPath(attrPath);
                    if (ua == null) {
                        throw new UnsupportedUserPath("Unsupported attribute: " + attrPath);
                    }
                    applyPatchValue(op, ua, existing, entry.getValue());
                }
                continue;
            }

            if (isReadOnlyOrStructural(path)) {
                continue;
            }

            UserAttribute<?> userAttribute = userAttributes.findByScimPath(path);
            if (userAttribute == null) {
                throw new UnsupportedUserPath("Unsupported attribute: " + path);
            }
            applyPatchValue(op, userAttribute, existing, value);
        }
    }

    /**
     * Apply a single PATCH operation (REPLACE/ADD/REMOVE) against one
     * resolved user attribute. Extracted so the path-less PatchOp shape
     * (RFC 7644 §3.5.2, map-valued "value") and the with-path shape share
     * the same write semantics.
     *
     * @param op       patch operation kind
     * @param attr     resolved user attribute target
     * @param existing user being patched
     * @param value    raw operation value
     */
    protected void applyPatchValue(
        PatchOperation op,
        UserAttribute<?> attr,
        UserModel existing,
        Object value
    ) {
        switch (op) {
            case REPLACE, ADD -> {
                switch (value) {
                    case null:
                        logger.warn("Value is null for patch operation: " + op);
                        break;
                    case String s when attr instanceof StringUserAttribute:
                        ((StringUserAttribute) attr).write(existing, s);
                        break;
                    case String s when attr instanceof BooleanUserAttribute:
                        ((BooleanUserAttribute) attr).write(existing, Boolean.parseBoolean(s));
                        break;
                    case Boolean b when attr instanceof BooleanUserAttribute:
                        ((BooleanUserAttribute) attr).write(existing, b);
                        break;
                    default:
                        logger.warn("Unsupported value type for patch operation: " + value.getClass() + " for SCIM path " + attr.getScimPath());
                        break;
                }
            }
            case REMOVE -> attr.clear(existing);
        }
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

        adminEventController.sendAdminEvent(
            scimContext,
            OperationType.CREATE,
            ResourceType.USER,
            "users/" + user.getId(),
            userRepresentation
        );
    }

    /**
     * Dispatches user deletion event
     *
     * @param scimContext SCIM context
     * @param user user
     */
    protected void dispatchUserDeleteEvent(
        ScimContext scimContext,
        UserModel user
    ) {
        adminEventController.sendAdminEvent(
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

        adminEventController.sendAdminEvent(
            scimContext,
            OperationType.UPDATE,
            ResourceType.USER,
            "users/" + user.getId(),
            userRepresentation
        );
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

        List<UserAttribute<?>> customAttributes = new ArrayList<>();
        customAttributes.addAll(userAttributes.listBySource(UserAttribute.Source.USER_PROFILE));
        customAttributes.addAll(userAttributes.listBySource(UserAttribute.Source.IDP_MAPPER));
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
     * Gets the external ID from SCIM user
     *
     * @param scimUser SCIM user
     * @return external ID or null if not set
     */
    private String getExternalId(User scimUser) {
        if (scimUser.getAdditionalProperties() == null) {
            return null;
        }

        Object externalIdObj = scimUser.getAdditionalProperty("externalId");
        if (!(externalIdObj instanceof String externalId)) {
            return null;
        }

        return externalId;
    }

    /**
     * Links user to identity provider
     *
     * @param session        Keycloak session
     * @param realm          Keycloak realm
     * @param user           Keycloak user
     * @param scimUserName   SCIM username
     * @param scimExternalId SCIM user external ID
     * @param idpAlias       Identity provider alias
     */
    private void linkUserIdp(
            KeycloakSession session,
            RealmModel realm,
            UserModel user,
            String scimUserName,
            String scimExternalId,
            String idpAlias
    ) {

        if (scimExternalId == null) {
            logger.warn("User externalId is not set. Cannot link user to identity provider");
            return;
        }
        IdentityProviderStorageProvider idpStorageProvider = session.getProvider(IdentityProviderStorageProvider.class);

        IdentityProviderModel identityProvider = idpStorageProvider.getByIdOrAlias(idpAlias);
        if (identityProvider == null) {
            logger.warn("Identity provider not found: " + idpAlias + ". Cannot link user to identity provider");
            return;
        }

        if (session.users().getFederatedIdentity(realm, user, identityProvider.getAlias()) == null) {
            logger.info("Linking user to identity provider: " + identityProvider.getAlias());

            FederatedIdentityModel identityModel = new FederatedIdentityModel(
                    identityProvider.getAlias(),
                    scimExternalId,
                    scimUserName
            );

            session.users().addFederatedIdentity(realm, user, identityModel);
        }
    }

    /**
     * Returns the email domain from the email address
     *
     * @param email email address
     * @return email domain
     */
    public static String getEmailDomain(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf('@') + 1);
        }

        return null;
    }
}

package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.metadata.BooleanUserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.StringUserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.patch.PatchOperation;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import fi.metatavu.keycloak.scim.server.users.UnsupportedUserPath;
import fi.metatavu.keycloak.scim.server.users.UsersController;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.*;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.organization.OrganizationProvider;

import java.util.Collections;
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
        OrganizationModel organization = scimContext.getOrganization();
        ScimConfig config = scimContext.getConfig();

        UserModel user = session.users().addUser(realm, scimUser.getUserName());
        user.setEnabled(scimUser.getActive() == null || Boolean.TRUE.equals(scimUser.getActive()));

        if (scimUser.getName() != null) {
            user.setFirstName(scimUser.getName().getGivenName());
            user.setLastName(scimUser.getName().getFamilyName());
        }

        String scimUserEmail = getScimUserEmail(scimUser, config);

        if (scimUserEmail != null) {
            user.setEmail(scimUserEmail);
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
        organizationProvider.addManagedMember(organization, user);

        User createdUser = translateUser(
            scimContext,
            userAttributes,
            user
        );

        if (config.getLinkIdp()) {
            String scimUsername = createdUser.getUserName();
            String externalId = getExternalId(createdUser);
            linkUserIdp(organizationProvider, organization, session, realm, user, scimUserEmail, scimUsername, externalId);
        }

        dispatchUserCreateEvent(scimContext, user);
        dispatchOrganizationMemberAddEvent(scimContext, user);

        return createdUser;
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
    public fi.metatavu.keycloak.scim.server.model.User updateOrganizationUser(
            OrganizationScimContext scimContext,
            UserAttributes userAttributes,
            UserModel existing,
            fi.metatavu.keycloak.scim.server.model.User scimUser
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();
        OrganizationModel organization = scimContext.getOrganization();
        ScimConfig config = scimContext.getConfig();

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

        User updatedUser = translateUser(
            scimContext,
            userAttributes,
            existing
        );

        if (config.getLinkIdp()) {
            OrganizationProvider organizationProvider = getOrganizationProvider(scimContext.getSession());
            String scimUserEmail = getScimUserEmail(updatedUser, config);
            String scimUsername = updatedUser.getUserName();
            String externalId = getExternalId(updatedUser);
            linkUserIdp(organizationProvider, organization, session, realm, existing, scimUserEmail, scimUsername, externalId);
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
    public fi.metatavu.keycloak.scim.server.model.User patchOrganizationUser(
        OrganizationScimContext scimContext,
        UserAttributes userAttributes,
        UserModel existing,
        fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest
    ) throws UnsupportedPatchOperation {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();
        OrganizationModel organization = scimContext.getOrganization();
        ScimConfig config = scimContext.getConfig();

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
                        case String s when userAttribute instanceof BooleanUserAttribute:
                            ((BooleanUserAttribute) userAttribute).write(existing, Boolean.parseBoolean(s));
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

        fi.metatavu.keycloak.scim.server.model.User patchedUser = translateUser(
            scimContext,
            userAttributes,
            existing
        );

        if (config.getLinkIdp()) {
            OrganizationProvider organizationProvider = getOrganizationProvider(scimContext.getSession());
            String scimUserEmail = getScimUserEmail(patchedUser, config);
            String scimUsername = patchedUser.getUserName();
            String externalId = getExternalId(patchedUser);
            linkUserIdp(organizationProvider, organization, session, realm, existing, scimUserEmail, scimUsername, externalId);
        }

        dispatchUserUpdateEvent(scimContext, existing);

        return patchedUser;
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

        RoleModel scimManagedRole = realm.getRole(ScimRoles.SCIM_MANAGED_ROLE);
        if (scimManagedRole == null) {
            throw new IllegalStateException("SCIM managed role not found");
        }

        List<UserModel> filteredUsers = getOrganizationProvider(session).getMembersStream(scimContext.getOrganization(), Collections.emptyMap(), true, null, null)
            .filter(user -> matchScimFilter(user, userAttributes, scimFilter))
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

        if (organizationProvider.isManagedMember(scimContext.getOrganization(), user)) {
            organizationProvider.removeMember(scimContext.getOrganization(), user);
            dispatchOrganizationMemberDeleteEvent(scimContext, user);
            dispatchUserDeleteEvent(scimContext, user);
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

    /**
     * Gets the email from SCIM user
     *
     * @param scimUser SCIM user
     * @param config SCIM configuration
     * @return user email
     */
    private String getScimUserEmail(fi.metatavu.keycloak.scim.server.model.User scimUser, ScimConfig config) {
        if (config.getEmailAsUsername()) {
            return scimUser.getUserName();
        }

        return scimUser.getEmails() != null && !scimUser.getEmails().isEmpty() ? scimUser.getEmails().getFirst().getValue() : null;
    }

    /**
     * Gets the external ID from SCIM user
     *
     * @param scimUser SCIM user
     * @return external ID or null if not set
     */
    private String getExternalId(fi.metatavu.keycloak.scim.server.model.User scimUser) {
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
     * @param organizationProvider organization provider
     * @param organization organization
     * @param session Keycloak session
     * @param realm Keycloak realm
     * @param user Keycloak user
     * @param scimUserEmail SCIM user email
     * @param scimUserName SCIM username
     * @param scimExternalId SCIM user external ID
     */
    private void linkUserIdp(
        OrganizationProvider organizationProvider,
        OrganizationModel organization,
        KeycloakSession session,
        RealmModel realm,
        UserModel user,
        String scimUserEmail,
        String scimUserName,
        String scimExternalId
    ) {
        if (scimUserEmail == null) {
            logger.warn("User email is not set. Cannot link user to identity provider");
            return;
        }

        if (scimExternalId == null) {
            logger.warn("User externalId is not set. Cannot link user to identity provider");
            return;
        }

        String emailDomain = getEmailDomain(scimUserEmail);
        if (emailDomain == null) {
            logger.warn("User email domain is not set. Cannot link user to identity provider");
            return;
        }

        IdentityProviderModel identityProvider = organizationProvider.getIdentityProviders(organization)
            .filter(identityProviderModel -> {
                String identityProviderDomain = identityProviderModel.getConfig().get("kc.org.domain");
                return identityProviderDomain != null && identityProviderDomain.equals(emailDomain);
            })
            .findFirst()
            .orElse(null);

        if (identityProvider == null) {
            logger.warn("No identity provider found for email domain: " + emailDomain + ". Cannot link user to identity provider");
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
     * Dispatches an event when a user is added to the organization
     *
     * @param scimContext SCIM context
     * @param member user that was added to the organization
     */
    private void dispatchOrganizationMemberAddEvent(
        OrganizationScimContext scimContext,
        UserModel member
    ) {
        OrganizationModel organization = scimContext.getOrganization();
        Map<String, String> eventDetails = new HashMap<>();

        if (member.getUsername() != null) {
            eventDetails.put(UserModel.USERNAME, member.getUsername());
        }

        if (member.getEmail() != null) {
            eventDetails.put(UserModel.EMAIL, member.getEmail());
        }

        sendAdminEvent(
            scimContext,
            OperationType.CREATE,
            ResourceType.ORGANIZATION_MEMBERSHIP,
            "organizations/" + organization.getId() + "/members",
            ModelToRepresentation.toRepresentation(organization),
            eventDetails
        );
    }

    /**
     * Dispatches an event when a user is removed from the organization
     *
     * @param scimContext SCIM context
     * @param member user that was deleted from the organization
     */
    private void dispatchOrganizationMemberDeleteEvent(
        OrganizationScimContext scimContext,
        UserModel member
    ) {
        OrganizationModel organization = scimContext.getOrganization();
        Map<String, String> eventDetails = new HashMap<>();

        if (member.getUsername() != null) {
            eventDetails.put(UserModel.USERNAME, member.getUsername());
        }

        if (member.getEmail() != null) {
            eventDetails.put(UserModel.EMAIL, member.getEmail());
        }

        sendAdminEvent(
            scimContext,
            OperationType.DELETE,
            ResourceType.ORGANIZATION_MEMBERSHIP,
            "organizations/" + organization.getId() + "/members/" + member.getId(),
            ModelToRepresentation.toRepresentation(organization),
            eventDetails
        );
    }
}

package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.metadata.BooleanUserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.StringUserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.users.UsersController;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.organization.OrganizationProvider;

import java.util.Collections;
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

        UserModel user = session.users().addUser(realm, scimUser.getUserName());
        user.setEnabled(scimUser.getActive() == null || Boolean.TRUE.equals(scimUser.getActive()));

        if (scimUser.getName() != null) {
            user.setFirstName(scimUser.getName().getGivenName());
            user.setLastName(scimUser.getName().getFamilyName());
        }

        String scimUserEmail = scimUser.getEmails() != null && !scimUser.getEmails().isEmpty() ? scimUser.getEmails().getFirst().getValue() : null;
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

        if (scimContext.getConfig().getLinkIdp()) {
            logger.info("Identity provider linking is enabled. Linking user to identity provider");
            linkUserIdp(scimUser, scimUserEmail, organizationProvider, organization, session, realm, user);
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

    /**
     * Links user to identity provider
     *
     * @param scimUser SCIM user
     * @param scimUserEmail user email
     * @param organizationProvider organization provider
     * @param organization organization
     * @param session Keycloak session
     * @param realm Keycloak realm
     * @param user Keycloak user
     */
    private void linkUserIdp(
            fi.metatavu.keycloak.scim.server.model.User scimUser,
            String scimUserEmail,
            OrganizationProvider organizationProvider,
            OrganizationModel organization,
            KeycloakSession session,
            RealmModel realm,
            UserModel user
    ) {
        if (scimUserEmail == null) {
            logger.warn("User email is not set. Cannot link user to identity provider");
            return;
        }

        String emailDomain = getEmailDomain(scimUserEmail);
        if (emailDomain == null) {
            logger.warn("User email domain is not set. Cannot link user to identity provider");
            return;
        }

        Object externalIdObj = scimUser.getAdditionalProperty("externalId");
        if (!(externalIdObj instanceof String externalId)) {
            logger.warn("User external ID is not set. Cannot link user to identity provider");
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

        logger.info("Linking user to identity provider: " + identityProvider.getAlias());

        FederatedIdentityModel identityModel = new FederatedIdentityModel(
            identityProvider.getAlias(),
            externalId,
            scimUser.getUserName()
        );

        session.users().addFederatedIdentity(realm, user, identityModel);
    }
}

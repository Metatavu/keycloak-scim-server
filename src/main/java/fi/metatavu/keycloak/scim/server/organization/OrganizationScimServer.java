package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.AbstractScimServer;
import fi.metatavu.keycloak.scim.server.ScimErrors;
import fi.metatavu.keycloak.scim.server.attributes.AttributeSelection;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.jacoco.ExcludeFromJacocoGeneratedReport;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.users.ScimManagedUsers;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

import java.net.URI;
import java.util.Objects;

/**
 * SCIM server implementation for organizations
 */
public abstract class OrganizationScimServer extends AbstractScimServer<OrganizationScimContext> {

    private static final Logger logger = Logger.getLogger(OrganizationScimServer.class);
    private final OrganizationUserController organizationUserController;

    public OrganizationScimServer() {
        this.organizationUserController = new OrganizationUserController();
    }

    @Override
    public Response createUser(OrganizationScimContext scimContext, User createRequest) {
        boolean emailAsUsername = scimContext.getConfig().getEmailAsUsername();

        if (isBlank(createRequest.getUserName())) {
            logger.warn("Cannot create user: Missing userName");
            return ScimErrors.invalidValue("Missing userName");
        }

        if (emailAsUsername && !isValidEmail(createRequest.getUserName())) {
            logger.warn("Cannot create user: Invalid email format for userName");
            return ScimErrors.invalidValue("Invalid email format for userName");
        }

        Response conflict = validateCreateUserConflicts(scimContext, createRequest);
        if (conflict != null) {
            return conflict;
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        return executeUserOperation(() -> {
            User user = organizationUserController.createOrganizationUser(scimContext, userAttributes, createRequest);
            URI location = scimContext.getServerBaseUri().resolve(String.format("v2/Users/%s", user.getId()));
            return Response.created(location).entity(user).build();
        });
    }

    @Override
    public Response updateUser(OrganizationScimContext scimContext, String userId, fi.metatavu.keycloak.scim.server.model.User updateRequest) {
        boolean emailAsUsername = scimContext.getConfig().getEmailAsUsername();
        KeycloakSession session = scimContext.getSession();
        String username = updateRequest.getUserName();

        if (isBlank(username)) {
            logger.warn("Missing userName");
            return ScimErrors.invalidValue("Missing userName");
        }

        if (emailAsUsername && !isValidEmail(updateRequest.getUserName())) {
            logger.warn("Cannot update user: Invalid email format for userName");
            return ScimErrors.invalidValue("Invalid email format for userName");
        }

        if (emailAsUsername && updateRequest.getEmails() != null) {
            for (fi.metatavu.keycloak.scim.server.model.UserEmailsInner email : updateRequest.getEmails()) {
                if (!Objects.equals(email.getValue(), updateRequest.getUserName())) {
                    logger.warn("Conflicting email and userName when emailAsUsername is enabled");
                    return ScimErrors.invalidValue("Username and email must match when emailAsUsername is enabled");
                }
            }
        }

        RealmModel realm = scimContext.getRealm();
        UserModel user = findScopedUser(scimContext, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return ScimErrors.notFound("User not found");
        }

        // Check if username is being changed to an already existing one
        UserModel existing;
        if (emailAsUsername) {
            existing = session.users().getUserByEmail(realm, updateRequest.getUserName());
        } else {
            existing = session.users().getUserByUsername(realm, updateRequest.getUserName());
        }

        if (existing != null && !existing.getId().equals(userId)) {
            logger.warn(String.format("User name already taken: %s", updateRequest.getUserName()));
            return ScimErrors.conflict("User name already taken");
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        return executeUserOperation(() -> {
            fi.metatavu.keycloak.scim.server.model.User result = organizationUserController.updateOrganizationUser(scimContext, userAttributes, user, updateRequest);
            return Response.ok(result).build();
        });
    }

    @Override
    public Response patchUser(OrganizationScimContext scimContext, String userId, fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest) {
        UserModel existing = findScopedUser(scimContext, userId);
        if (existing == null) {
            logger.warn(String.format("User not found: %s", userId));
            return ScimErrors.notFound("User not found");
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        return executeUserOperation(() -> {
            fi.metatavu.keycloak.scim.server.model.User result = organizationUserController.patchOrganizationUser(scimContext, userAttributes, existing, patchRequest);
            return Response.ok(result).build();
        });
    }

    @Override
    public Response listUsers(OrganizationScimContext scimContext, ScimFilter scimFilter, Integer firstResult, Integer maxResults) {
        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        fi.metatavu.keycloak.scim.server.model.UsersList usersList = organizationUserController.listOrganizationUsers(
            scimContext,
            scimFilter,
            userAttributes,
            firstResult,
            maxResults
        );

        return Response.ok(usersList).build();
    }

    @Override
    public Response findUser(OrganizationScimContext scimContext, String userId) {
        if (findScopedUser(scimContext, userId) == null) {
            logger.warn(String.format("User not found: %s", userId));
            return ScimErrors.notFound("User not found");
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);
        User user = organizationUserController.findOrganizationUser(scimContext, userAttributes, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return ScimErrors.notFound("User not found");
        }

        return Response.ok(user).build();
    }

    @Override
    public Response deleteUser(OrganizationScimContext scimContext, String userId) {
        RealmModel realm = scimContext.getRealm();
        KeycloakSession session = scimContext.getSession();

        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!ScimManagedUsers.isManaged(scimContext, user)) {
            logger.warnf("Refusing to delete user %s: not managed by SCIM (SCIM_MANAGE_ALL_USERS=%s)", userId, scimContext.getConfig().getManageAllUsers());
            return ScimErrors.forbidden("User is not managed by SCIM");
        }

        organizationUserController.deleteOrganizationUser(scimContext, user);

        return Response.noContent().build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response createGroup(OrganizationScimContext scimContext, Group createRequest) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response listGroups(OrganizationScimContext scimContext, ScimFilter filter, int firstResult, int maxResults, AttributeSelection attributeSelection) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response findGroup(OrganizationScimContext scimContext, String id, AttributeSelection attributeSelection) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response updateGroup(OrganizationScimContext scimContext, String id, Group updateRequest) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response patchGroup(OrganizationScimContext scimContext, String groupId, PatchRequest patchRequest, AttributeSelection attributeSelection) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response deleteGroup(OrganizationScimContext scimContext, String id) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    public abstract OrganizationScimContext getScimContext(KeycloakSession session, String organizationId);

    /**
     * Finds a user that is within the organization SCIM API's scope.
     *
     * The user must be a member of this organization and managed through SCIM (see {@link ScimManagedUsers}),
     * which is the same scope the user list applies. Users outside that scope, including users that only
     * belong to other organizations, are treated as if they did not exist.
     *
     * @param scimContext SCIM context
     * @param userId user ID
     * @return user, or null if the user does not exist or is outside the organization's SCIM scope
     */
    private UserModel findScopedUser(OrganizationScimContext scimContext, String userId) {
        UserModel user = scimContext.getSession().users().getUserById(scimContext.getRealm(), userId);
        if (user == null) {
            return null;
        }

        if (!scimContext.isMember(user) || !ScimManagedUsers.isManaged(scimContext, user)) {
            logger.warnf("User %s is not managed by this organization's SCIM (SCIM_MANAGE_ALL_USERS=%s), treating it as not found", userId, scimContext.getConfig().getManageAllUsers());
            return null;
        }

        return user;
    }

}

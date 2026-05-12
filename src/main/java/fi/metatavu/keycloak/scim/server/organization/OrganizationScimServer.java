package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.AbstractScimServer;
import fi.metatavu.keycloak.scim.server.ScimErrors;
import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.jacoco.ExcludeFromJacocoGeneratedReport;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
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
            return ScimErrors.badRequest("Missing userName");
        }

        if (emailAsUsername && !isValidEmail(createRequest.getUserName())) {
            logger.warn("Cannot create user: Invalid email format for userName");
            return ScimErrors.badRequest("Invalid email format for userName");
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        User user = organizationUserController.createOrganizationUser(
            scimContext,
            userAttributes,
            createRequest
        );

        URI location = scimContext.getServerBaseUri().resolve(String.format("v2/Users/%s", user.getId()));

        return Response
            .created(location)
            .entity(user)
            .build();
    }

    @Override
    public Response updateUser(OrganizationScimContext scimContext, String userId, fi.metatavu.keycloak.scim.server.model.User updateRequest) {
        boolean emailAsUsername = scimContext.getConfig().getEmailAsUsername();
        KeycloakSession session = scimContext.getSession();
        String username = updateRequest.getUserName();

        if (isBlank(username)) {
            logger.warn("Missing userName");
            return ScimErrors.badRequest("Missing userName");
        }

        if (emailAsUsername && !isValidEmail(updateRequest.getUserName())) {
            logger.warn("Cannot update user: Invalid email format for userName");
            return ScimErrors.badRequest("Invalid email format for userName");
        }

        if (emailAsUsername && updateRequest.getEmails() != null) {
            for (fi.metatavu.keycloak.scim.server.model.UserEmailsInner email : updateRequest.getEmails()) {
                if (!Objects.equals(email.getValue(), updateRequest.getUserName())) {
                    logger.warn("Conflicting email and userName when emailAsUsername is enabled");
                    return ScimErrors.badRequest("Username and email must match when emailAsUsername is enabled");
                }
            }
        }

        RealmModel realm = scimContext.getRealm();
        UserModel user = session.users().getUserById(realm, userId);
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
        fi.metatavu.keycloak.scim.server.model.User result = organizationUserController.updateOrganizationUser(scimContext, userAttributes, user, updateRequest);

        return Response.ok(result).build();
    }

    @Override
    public Response patchUser(OrganizationScimContext scimContext, String userId, fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest) {
        KeycloakSession session = scimContext.getSession();

        RealmModel realm = scimContext.getRealm();
        UserModel existing = session.users().getUserById(realm, userId);
        if (existing == null) {
            logger.warn(String.format("User not found: %s", userId));
            return ScimErrors.notFound("User not found");
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        try {
            fi.metatavu.keycloak.scim.server.model.User result = organizationUserController.patchOrganizationUser(scimContext, userAttributes, existing, patchRequest);
            return Response.ok(result).build();
        } catch (UnsupportedPatchOperation e) {
            return ScimErrors.badRequest("Unsupported patch operation");
        }
    }

    @Override
    public Response listUsers(OrganizationScimContext scimContext, ScimFilter scimFilter, Integer startIndex, Integer count) {
        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        fi.metatavu.keycloak.scim.server.model.UsersList usersList = organizationUserController.listOrganizationUsers(
            scimContext,
            scimFilter,
            userAttributes,
            startIndex,
            count
        );

        return Response.ok(usersList).build();
    }

    @Override
    public Response findUser(OrganizationScimContext scimContext, String userId) {
        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);
        User user = organizationUserController.findOrganizationUser(scimContext, userAttributes, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).build();
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

        RoleModel scimManagedRole = realm.getRole("scim-managed");
        if (scimManagedRole != null && !user.hasRole(scimManagedRole)) {
            logger.warn(String.format("User is not SCIM-managed: %s", userId));
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
    public Response listGroups(OrganizationScimContext scimContext, ScimFilter filter, int startIndex, int count) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response findGroup(OrganizationScimContext scimContext, String id) {
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
    public Response patchGroup(OrganizationScimContext scimContext, String groupId, PatchRequest patchRequest) {
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

}

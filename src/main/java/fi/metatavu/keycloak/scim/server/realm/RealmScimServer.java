package fi.metatavu.keycloak.scim.server.realm;

import fi.metatavu.keycloak.scim.server.AbstractScimServer;
import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.groups.UnsupportedGroupPath;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

import java.net.URI;

/**
 * SCIM server implementation for realms
 */
public class RealmScimServer extends AbstractScimServer<RealmScimContext> {

    private static final Logger logger = Logger.getLogger(RealmScimServer.class.getName());

    @Override
    public Response createUser(
        RealmScimContext scimContext,
        User createRequest
    ) {
        RealmModel realm = scimContext.getRealm();
        KeycloakSession session = scimContext.getSession();

        if (createRequest.getUserName().isBlank()) {
            logger.warn("Cannot create user: Missing userName");
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing userName").build();
        }

        UserModel existing = session.users().getUserByUsername(realm, createRequest.getUserName());
        if (existing != null) {
            return Response.status(Response.Status.CONFLICT).entity("User already exists").build();
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        User user = usersController.createUser(
            scimContext,
            userAttributes,
            createRequest
        );

        URI location = UriBuilder.fromPath("v2/Users/{id}").build(user.getId());

        return Response
            .created(location)
            .entity(user)
            .build();
    }

    @Override
    public Response listUsers(RealmScimContext scimContext, ScimFilter scimFilter, Integer startIndex, Integer count) {
        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        fi.metatavu.keycloak.scim.server.model.UsersList usersList = usersController.listUsers(
            scimContext,
            scimFilter,
            userAttributes,
            startIndex,
            count
        );

        return Response.ok(usersList).build();
    }

    @Override
    public Response findUser(RealmScimContext scimContext, String userId) {
        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);
        User user = usersController.findUser(scimContext, userAttributes, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(user).build();
    }

    @Override
    public Response deleteUser(RealmScimContext scimContext, String userId) {
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
            return Response.status(Response.Status.FORBIDDEN).entity("User is not managed by SCIM").build();
        }

        usersController.deleteUser(scimContext, user);

        return Response.noContent().build();
    }

    @Override
    public Response createGroup(RealmScimContext scimContext, fi.metatavu.keycloak.scim.server.model.Group createRequest) {
        // TODO: conflict check

        fi.metatavu.keycloak.scim.server.model.Group created = groupsController.createGroup(scimContext, createRequest);
        URI location = UriBuilder.fromPath("v2/Groups/{id}").build(created.getId());

        return Response
                .created(location)
                .entity(created)
                .build();
    }

    @Override
    public Response listGroups(RealmScimContext scimContext, int startIndex, int count) {
        fi.metatavu.keycloak.scim.server.model.GroupsList groupList = groupsController.listGroups(scimContext, startIndex, count);
        return Response.ok(groupList).build();
    }

    @Override
    public Response findGroup(RealmScimContext scimContext, String id) {
        fi.metatavu.keycloak.scim.server.model.Group group = groupsController.findGroup(scimContext, id);
        if (group == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(group).build();
    }

    @Override
    public Response updateGroup(RealmScimContext scimContext, String id, fi.metatavu.keycloak.scim.server.model.Group updateRequest) {
        KeycloakSession session = scimContext.getSession();

        GroupModel existing = session.groups().getGroupById(scimContext.getRealm(), id);
        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!id.equals(existing.getId())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Group ID mismatch").build();
        }

        fi.metatavu.keycloak.scim.server.model.Group updated = groupsController.updateGroup(
            scimContext,
            existing,
            updateRequest
        );

        return Response.ok(updated).build();
    }

    @Override
    public Response patchGroup(RealmScimContext scimContext, String groupId, fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest) {
        KeycloakSession session = scimContext.getSession();

        GroupModel existing = session.groups().getGroupById(scimContext.getRealm(), groupId);
        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!groupId.equals(existing.getId())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Group ID mismatch").build();
        }

        try {
            fi.metatavu.keycloak.scim.server.model.Group updated = groupsController.patchGroup(scimContext, existing, patchRequest);
            return Response.ok(updated).build();
        } catch (UnsupportedGroupPath e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Unsupported group path").build();
        } catch (UnsupportedPatchOperation e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Unsupported patch operation").build();
        }
    }

    @Override
    public Response deleteGroup(RealmScimContext scimContext, String id) {
        KeycloakSession session = scimContext.getSession();
        GroupModel group = session.groups().getGroupById(scimContext.getRealm(), id);
        if (group == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        groupsController.deleteGroup(scimContext, group);

        return Response.noContent().build();
    }

    /**
     * Returns SCIM context
     *
     * @param session Keycloak session
     * @return SCIM context
     */
    public RealmScimContext getScimContext(KeycloakSession session) {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            throw new NotFoundException("Realm not found");
        }

        URI serverBaseUri = session.getContext().getUri().getBaseUri().resolve(String.format("realms/%s/scim/v2/", realm.getName()));
        RealmScimConfig config = new RealmScimConfig();

        try {
            config.validateConfig();
        } catch (ConfigurationError e) {
            throw new InternalServerErrorException("Invalid SCIM configuration", e);
        }

        return new RealmScimContext(
            serverBaseUri,
            session,
            realm,
            config
        );
    }

}

package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.AbstractScimServer;
import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.model.User;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

import java.net.URI;

/**
 * SCIM server implementation for organizations
 */
public class OrganizationScimServer extends AbstractScimServer<OrganizationScimContext> {

    private static final Logger logger = Logger.getLogger(OrganizationScimServer.class);
    private final OrganizationController organizationController;
    private final OrganizationUserController organizationUserController;

    public OrganizationScimServer() {
        this.organizationController = new OrganizationController();
        this.organizationUserController = new OrganizationUserController();
    }

    @Override
    public Response createUser(OrganizationScimContext scimContext, User createRequest) {
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
            return Response.status(Response.Status.FORBIDDEN).entity("User is not managed by SCIM").build();
        }

        organizationUserController.deleteOrganizationUser(scimContext, user);

        return Response.noContent().build();
    }

    @Override
    public Response createGroup(OrganizationScimContext scimContext, Group createRequest) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    public Response listGroups(OrganizationScimContext scimContext, int startIndex, int count) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    public Response findGroup(OrganizationScimContext scimContext, String id) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    public Response updateGroup(OrganizationScimContext scimContext, String id, Group updateRequest) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    public Response patchGroup(OrganizationScimContext scimContext, String groupId, PatchRequest patchRequest) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    public Response deleteGroup(OrganizationScimContext scimContext, String id) {
        // TODO: Organization Groups are not supported yet by the Keycloak
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    /**
     * Returns SCIM context
     *
     * @param session Keycloak session
     * @return SCIM context
     */
    public OrganizationScimContext getScimContext(KeycloakSession session, String organizationId) {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            throw new NotFoundException("Realm not found");
        }

        OrganizationModel organization = organizationController.findOrganizationById(
            session,
            organizationId
        );

        if (organization == null) {
            throw new NotFoundException("Organization not found");
        }

        KeycloakContext context = session.getContext();
        context.setOrganization(organization);

        URI baseUri = session.getContext().getUri().getBaseUri().resolve(String.format("realms/%s/scim/v2/organizations/%s/", realm.getName(), organization.getId()));
        OrganizationScimConfig config = new OrganizationScimConfig(organization);

        try {
            config.validateConfig();
        } catch (ConfigurationError e) {
            throw new InternalServerErrorException("Invalid SCIM configuration", e);
        }

        return new OrganizationScimContext(
            baseUri,
            session,
            realm,
            organization,
            config
        );
    }

}

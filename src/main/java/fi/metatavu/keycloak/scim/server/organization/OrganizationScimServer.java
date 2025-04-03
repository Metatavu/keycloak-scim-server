package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.AbstractScimServer;
import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.model.User;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.RealmModel;
import org.keycloak.organization.OrganizationProvider;

import java.net.URI;

/**
 * SCIM server implementation for organizations
 */
public class OrganizationScimServer extends AbstractScimServer<OrganizationScimContext> {

    @Override
    public Response createUser(OrganizationScimContext scimContext, User user) {
        return null;
    }

    @Override
    public Response listUsers(OrganizationScimContext scimContext, ScimFilter scimFilter, Integer startIndex, Integer count) {
        return null;
    }

    @Override
    public Response findUser(OrganizationScimContext scimContext, String userId) {
        return null;
    }

    @Override
    public Response updateUser(OrganizationScimContext scimContext, String userId, User body) {
        return null;
    }

    @Override
    public Response patchUser(OrganizationScimContext scimContext, String userId, PatchRequest patchRequest) {
        return null;
    }

    @Override
    public Response deleteUser(OrganizationScimContext scimContext, String userId) {
        return null;
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

        OrganizationProvider provider = session.getProvider(OrganizationProvider.class);
        OrganizationModel organization = provider.getById(organizationId);
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

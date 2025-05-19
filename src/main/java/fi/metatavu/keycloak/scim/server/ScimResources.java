package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.consts.ContentTypes;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilterParser;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.organization.OrganizationScimContext;
import fi.metatavu.keycloak.scim.server.organization.OrganizationScimServer;
import fi.metatavu.keycloak.scim.server.realm.RealmScimContext;
import fi.metatavu.keycloak.scim.server.realm.RealmScimServer;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

/**
 * SCIM REST resources
 */
public class ScimResources {

    private static final Logger logger = Logger.getLogger(ScimResources.class.getName());
    private final ScimFilterParser scimFilterParser;
    private final RealmScimServer realmScimServer;
    private final OrganizationScimServer organizationScimServer;

    ScimResources() {
        scimFilterParser = new ScimFilterParser();
        realmScimServer = new RealmScimServer();
        organizationScimServer = new OrganizationScimServer();
    }

    // Realm Server endpoints

    @POST
    @Path("v2/Users")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response createRealmUser(
        @Context KeycloakSession session,
        fi.metatavu.keycloak.scim.server.model.User createRequest
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.createUser(
            scimContext,
            createRequest
        );
    }

    @GET
    @Path("v2/Users")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listRealmUsers(
        @Context KeycloakSession session,
        @QueryParam("filter") String filter,
        @QueryParam("startIndex") @DefaultValue("0") Integer startIndex,
        @QueryParam("count") @DefaultValue("100") Integer count
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        ScimFilter scimFilter;
        try {
            scimFilter = parseFilter(filter);
        } catch (Exception e) {
            logger.warn(String.format("Failed to parse filter: '%s'", filter), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid filter").build();
        }

        return realmScimServer.listUsers(
            scimContext,
            scimFilter,
            startIndex,
            count
        );
    }

    @GET
    @Path("v2/Users/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response findRealmUser(
            @Context KeycloakSession session,
            @PathParam("id") String userId
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.findUser(
            scimContext,
            userId
        );
    }

    @PUT
    @Path("v2/Users/{id}")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response updateRealmUser(
        @Context KeycloakSession session,
        @PathParam("id") String userId,
        fi.metatavu.keycloak.scim.server.model.User updateRequest
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.updateUser(
            scimContext,
            userId,
            updateRequest
        );
    }

    @PATCH
    @Path("v2/Users/{id}")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response patchRealmUser(
        @Context KeycloakSession session,
        @PathParam("id") String userId,
        fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.patchUser(
            scimContext,
            userId,
            patchRequest
        );
    }

    @DELETE
    @Path("v2/Users/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response deleteRealmUser(
        @Context KeycloakSession session,
        @PathParam("id") String userId
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.deleteUser(scimContext, userId);
    }

    @POST
    @Path("v2/Groups")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response createRealmGroup(
        @Context KeycloakSession session,
        fi.metatavu.keycloak.scim.server.model.Group createRequest
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.createGroup(
            scimContext,
            createRequest
        );
    }

    @GET
    @Path("v2/Groups")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listRealmGroups(
            @Context KeycloakSession session,
            @QueryParam("startIndex") @DefaultValue("0") int startIndex,
            @QueryParam("count") @DefaultValue("100") int count
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.listGroups(
                scimContext,
                startIndex,
                count
        );
    }

    @GET
    @Path("v2/Groups/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response findRealmGroup(
            @Context KeycloakSession session,
            @PathParam("id") String id
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.findGroup(
                scimContext,
                id
        );
    }

    @PUT
    @Path("v2/Groups/{id}")
    @Consumes("application/scim+json")
    @Produces("application/scim+json")
    @SuppressWarnings("unused")
    public Response updateRealmGroup(
            @PathParam("id") String id,
            @Context KeycloakSession session,
            Group updateRequest
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.updateGroup(
                scimContext,
                id,
                updateRequest
        );
    }

    @PATCH
    @Path("v2/Groups/{id}")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response patchRealmGroup(
            @Context KeycloakSession session,
            @PathParam("id") String groupId,
            fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.patchGroup(
                scimContext,
                groupId,
                patchRequest
        );
    }

    @DELETE
    @Path("v2/Groups/{id}")
    @SuppressWarnings("unused")
    public Response deleteRealmGroup(
            @Context KeycloakSession session,
            @PathParam("id") String id
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.deleteGroup(
                scimContext,
                id
        );
    }

    @GET
    @Path("v2/ResourceTypes")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listRealmResourceTypes(
        @Context KeycloakSession session,
        @Context UriInfo uriInfo
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.listResourceTypes(scimContext);
    }

    @GET
    @Path("v2/ResourceTypes/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response findRealmResourceType(
        @Context KeycloakSession session,
        @PathParam("id") String id
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.findResourceType(
                scimContext,
                id
        );
    }

    @GET
    @Path("v2/Schemas")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listRealmSchemas(
        @Context KeycloakSession session,
        @Context UriInfo uriInfo
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.listSchemas(scimContext);
    }

    @GET
    @Path("v2/Schemas/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response findRealmSchema(
        @Context KeycloakSession session,
        @PathParam("id") String id
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.findSchema(
                scimContext,
                id
        );
    }

    @GET
    @Path("v2/ServiceProviderConfig")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response getRealmServiceProviderConfig(
        @Context KeycloakSession session,
        @Context UriInfo uriInfo
    ) {
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.getServiceProviderConfig(scimContext);
    }

    // Organization Server endpoints

    @POST
    @Path("v2/organizations/{organizationId}/Users")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response createOrganizationUser(
            @Context KeycloakSession session,
            @PathParam("organizationId") String organizationId,
            fi.metatavu.keycloak.scim.server.model.User createRequest
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.createUser(
            scimContext,
            createRequest
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Users")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listOrganizationUsers(
            @Context KeycloakSession session,
            @PathParam("organizationId") String organizationId,
            @QueryParam("filter") String filter,
            @QueryParam("startIndex") @DefaultValue("0") Integer startIndex,
            @QueryParam("count") @DefaultValue("100") Integer count
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        ScimFilter scimFilter;
        try {
            scimFilter = parseFilter(filter);
        } catch (Exception e) {
            logger.warn(String.format("Failed to parse filter: '%s'", filter), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid filter").build();
        }

        return organizationScimServer.listUsers(
            scimContext,
            scimFilter,
            startIndex,
            count
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Users/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response findOrganizationUser(
            @Context KeycloakSession session,
            @PathParam("id") String userId,
            @PathParam("organizationId") String organizationId
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.findUser(
            scimContext,
            userId
        );
    }

    @PUT
    @Path("v2/organizations/{organizationId}/Users/{id}")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response updateOrganizationUser(
            @Context KeycloakSession session,
            @PathParam("id") String userId,
            @PathParam("organizationId") String organizationId,
            fi.metatavu.keycloak.scim.server.model.User updateRequest
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.updateUser(
            scimContext,
            userId,
            updateRequest
        );
    }

    @PATCH
    @Path("v2/organizations/{organizationId}/Users/{id}")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response patchOrganizationUser(
            @Context KeycloakSession session,
            @PathParam("id") String userId,
            @PathParam("organizationId") String organizationId,
            fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.patchUser(
                scimContext,
                userId,
                patchRequest
        );
    }

    @DELETE
    @Path("v2/organizations/{organizationId}/Users/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response deleteOrganizationUser(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @PathParam("id") String userId
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.deleteUser(scimContext, userId);
    }

    @POST
    @Path("v2/organizations/{organizationId}/Groups")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response createOrganizationGroup(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        fi.metatavu.keycloak.scim.server.model.Group createRequest
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.createGroup(
            scimContext,
            createRequest
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Groups")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listOrganizationGroups(
            @Context KeycloakSession session,
            @PathParam("organizationId") String organizationId,
            @QueryParam("startIndex") @DefaultValue("0") int startIndex,
            @QueryParam("count") @DefaultValue("100") int count
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.listGroups(
            scimContext,
            startIndex,
            count
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Groups/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response findOrganizationGroup(
            @Context KeycloakSession session,
            @PathParam("organizationId") String organizationId,
            @PathParam("id") String id
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.findGroup(
            scimContext,
            id
        );
    }

    @PUT
    @Path("v2/organizations/{organizationId}/Groups/{id}")
    @Consumes("application/scim+json")
    @Produces("application/scim+json")
    @SuppressWarnings("unused")
    public Response updateOrganizationGroup(
            @Context KeycloakSession session,
            @PathParam("id") String id,
            @PathParam("organizationId") String organizationId,
            Group updateRequest
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.updateGroup(
            scimContext,
            id,
            updateRequest
        );
    }

    @PATCH
    @Path("v2/organizations/{organizationId}/Groups/{id}")
    @Consumes(ContentTypes.APPLICATION_SCIM_JSON)
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response patchOrganizationGroup(
            @Context KeycloakSession session,
            @PathParam("id") String groupId,
            @PathParam("organizationId") String organizationId,
            fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.patchGroup(
                scimContext,
                groupId,
                patchRequest
        );
    }

    @DELETE
    @Path("v2/organizations/{organizationId}/Groups/{id}")
    @SuppressWarnings("unused")
    public Response deleteOrganizationGroup(
            @Context KeycloakSession session,
            @PathParam("organizationId") String organizationId,
            @PathParam("id") String id
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.deleteGroup(
            scimContext,
            id
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/ResourceTypes")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listOrganizationResourceTypes(
        @Context KeycloakSession session,
        @Context UriInfo uriInfo,
        @PathParam("organizationId") String organizationId
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.listResourceTypes(
            scimContext
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/ResourceTypes/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response findOrganizationResourceType(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @PathParam("id") String id
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.findResourceType(
            scimContext,
            id
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Schemas")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response listOrganizationSchemas(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @Context UriInfo uriInfo
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.listSchemas(
            scimContext
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Schemas/{id}")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response findOrganizationSchema(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @PathParam("id") String id
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);

        return organizationScimServer.findSchema(
            scimContext,
            id
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/ServiceProviderConfig")
    @Produces(ContentTypes.APPLICATION_SCIM_JSON)
    @SuppressWarnings("unused")
    public Response getOrganizationServiceProviderConfig(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @Context UriInfo uriInfo
    ) {
        OrganizationScimContext scimContext = organizationScimServer.getScimContext(session, organizationId);
        organizationScimServer.verifyPermissions(scimContext);
        return organizationScimServer.getServiceProviderConfig(scimContext);
    }

    /**
     * Parses SCIM filter
     *
     * @param filter filter
     * @return parsed filter or null if filter is not defined
     */
    private ScimFilter parseFilter(String filter) {
        if (filter != null && !filter.isBlank()) {
            return scimFilterParser.parse(filter);
        }

        return null;
    }

}

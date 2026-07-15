package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.consts.ContentTypes;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilterParser;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.organization.OrganizationScimContext;
import fi.metatavu.keycloak.scim.server.organization.OrganizationScimServer;
import fi.metatavu.keycloak.scim.server.organization.OrganizationScimServerProvider;
import fi.metatavu.keycloak.scim.server.realm.RealmScimContext;
import fi.metatavu.keycloak.scim.server.realm.RealmScimServer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.util.JsonSerialization;

/**
 * SCIM REST resources
 */
public class ScimResources {

    private static final Logger logger = Logger.getLogger(ScimResources.class.getName());
    private static final String LOG_REQUESTS_ENV = "SCIM_LOG_INCOMING_REQUESTS";
    private static final String LOG_SENSITIVE_ENV = "SCIM_LOG_SENSITIVE_HEADERS";
    private final ScimFilterParser scimFilterParser;
    private final RealmScimServer realmScimServer;
    private final KeycloakSession session;
    private OrganizationScimServer organizationScimServer;

    ScimResources(KeycloakSession session) {
        this.session = session;
        scimFilterParser = new ScimFilterParser();
        realmScimServer = new RealmScimServer();
    }

    private OrganizationScimServer getOrganizationScimServer() {
        if (organizationScimServer == null) {
            try {
                OrganizationScimServerProvider provider = session.getProvider(OrganizationScimServerProvider.class);
                if (provider == null) {
                    throw new NotFoundException("No OrganizationScimServerProvider is registered. Organization SCIM endpoints are not available.");
                }
                organizationScimServer = provider.getScimServer(session);
            } catch (NotFoundException e) {
                throw e;
            } catch (Exception e) {
                logger.warn("Failed to load OrganizationScimServerProvider. Organization SCIM endpoints will not be available.", e);
                throw new NotFoundException("Organization SCIM endpoints are not available.");
            }
        }
        return organizationScimServer;
    }

    // Realm Server endpoints

    @POST
    @Path("v2/Users")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response createRealmUser(
        @Context KeycloakSession session,
        @Context HttpHeaders headers,
        @Context UriInfo uriInfo,
        String rawRequest
    ) {
        logger.debug("POST /v2/Users");
        logIncomingRequest("POST", uriInfo, headers, rawRequest);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.createUser(
            scimContext,
            parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.User.class)
        );
    }

    @GET
    @Path("v2/Users")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response listRealmUsers(
        @Context KeycloakSession session,
        @QueryParam("filter") String filter,
        @QueryParam("startIndex") @DefaultValue("1") Integer startIndex,
        @QueryParam("count") @DefaultValue("100") Integer count
    ) {
        logger.debugf("GET /v2/Users filter=%s startIndex=%d count=%d", filter, startIndex, count);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        ScimFilter scimFilter;
        try {
            scimFilter = parseFilter(filter);
        } catch (Exception e) {
            logger.warn(String.format("Failed to parse filter: '%s'", filter), e);
            return ScimErrors.invalidFilter("Invalid filter");
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
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response findRealmUser(
            @Context KeycloakSession session,
            @PathParam("id") String userId
    ) {
        logger.debugf("GET /v2/Users/%s", userId);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.findUser(
            scimContext,
            userId
        );
    }

    @PUT
    @Path("v2/Users/{id}")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response updateRealmUser(
        @Context KeycloakSession session,
        @Context HttpHeaders headers,
        @Context UriInfo uriInfo,
        @PathParam("id") String userId,
        String rawRequest
    ) {
        logger.debugf("PUT /v2/Users/%s", userId);
        logIncomingRequest("PUT", uriInfo, headers, rawRequest);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.updateUser(
            scimContext,
            userId,
            parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.User.class)
        );
    }

    @PATCH
    @Path("v2/Users/{id}")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response patchRealmUser(
        @Context KeycloakSession session,
        @Context HttpHeaders headers,
        @Context UriInfo uriInfo,
        @PathParam("id") String userId,
        String rawRequest
    ) {
        logger.debugf("PATCH /v2/Users/%s", userId);
        logIncomingRequest("PATCH", uriInfo, headers, rawRequest);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.patchUser(
            scimContext,
            userId,
            parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.PatchRequest.class)
        );
    }

    @DELETE
    @Path("v2/Users/{id}")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response deleteRealmUser(
        @Context KeycloakSession session,
        @PathParam("id") String userId
    ) {
        logger.debugf("DELETE /v2/Users/%s", userId);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.deleteUser(scimContext, userId);
    }

    @POST
    @Path("v2/Groups")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response createRealmGroup(
        @Context KeycloakSession session,
        @Context HttpHeaders headers,
        @Context UriInfo uriInfo,
        String rawRequest
    ) {
        logger.debug("POST /v2/Groups");
        logIncomingRequest("POST", uriInfo, headers, rawRequest);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.createGroup(
            scimContext,
            parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.Group.class)
        );
    }

    @GET
    @Path("v2/Groups")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response listRealmGroups(
            @Context KeycloakSession session,
            @QueryParam("filter") String filter,
            @QueryParam("startIndex") @DefaultValue("1") int startIndex,
            @QueryParam("count") @DefaultValue("100") int count
    ) {
        logger.debugf("GET /v2/Groups filter=%s startIndex=%d count=%d", filter, startIndex, count);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        ScimFilter scimFilter;
        try {
            scimFilter = parseFilter(filter);
        } catch (Exception e) {
            logger.warn(String.format("Failed to parse filter: '%s'", filter), e);
            return ScimErrors.invalidFilter("Invalid filter");
        }

        return realmScimServer.listGroups(
                scimContext,
                scimFilter,
                startIndex,
                count
        );
    }

    @GET
    @Path("v2/Groups/{id}")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response findRealmGroup(
            @Context KeycloakSession session,
            @PathParam("id") String id
    ) {
        logger.debugf("GET /v2/Groups/%s", id);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.findGroup(
                scimContext,
                id
        );
    }

    @PUT
    @Path("v2/Groups/{id}")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response updateRealmGroup(
            @PathParam("id") String id,
            @Context KeycloakSession session,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            String rawRequest
    ) {
        logger.debugf("PUT /v2/Groups/%s", id);
        logIncomingRequest("PUT", uriInfo, headers, rawRequest);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.updateGroup(
                scimContext,
                id,
                parseBody(rawRequest, Group.class)
        );
    }

    @PATCH
    @Path("v2/Groups/{id}")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response patchRealmGroup(
            @Context KeycloakSession session,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            @PathParam("id") String groupId,
            String rawRequest
    ) {
        logger.debugf("PATCH /v2/Groups/%s", groupId);
        logIncomingRequest("PATCH", uriInfo, headers, rawRequest);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.patchGroup(
                scimContext,
                groupId,
                parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.PatchRequest.class)
        );
    }

    @DELETE
    @Path("v2/Groups/{id}")
    @SuppressWarnings("unused")
    public Response deleteRealmGroup(
            @Context KeycloakSession session,
            @PathParam("id") String id
    ) {
        logger.debugf("DELETE /v2/Groups/%s", id);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.deleteGroup(
                scimContext,
                id
        );
    }

    @GET
    @Path("v2/ResourceTypes")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response listRealmResourceTypes(
        @Context KeycloakSession session,
        @Context UriInfo uriInfo
    ) {
        logger.debug("GET /v2/ResourceTypes");
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.listResourceTypes(scimContext);
    }

    @GET
    @Path("v2/ResourceTypes/{id}")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response findRealmResourceType(
        @Context KeycloakSession session,
        @PathParam("id") String id
    ) {
        logger.debugf("GET /v2/ResourceTypes/%s", id);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.findResourceType(
                scimContext,
                id
        );
    }

    @GET
    @Path("v2/Schemas")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response listRealmSchemas(
        @Context KeycloakSession session,
        @Context UriInfo uriInfo
    ) {
        logger.debug("GET /v2/Schemas");
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.listSchemas(scimContext);
    }

    @GET
    @Path("v2/Schemas/{id}")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response findRealmSchema(
        @Context KeycloakSession session,
        @PathParam("id") String id
    ) {
        logger.debugf("GET /v2/Schemas/%s", id);
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.findSchema(
                scimContext,
                id
        );
    }

    @GET
    @Path("v2/ServiceProviderConfig")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response getRealmServiceProviderConfig(
        @Context KeycloakSession session,
        @Context UriInfo uriInfo
    ) {
        logger.debug("GET /v2/ServiceProviderConfig");
        RealmScimContext scimContext = realmScimServer.getScimContext(session);
        realmScimServer.verifyPermissions(scimContext);

        return realmScimServer.getServiceProviderConfig(scimContext);
    }

    // Organization Server endpoints

    @POST
    @Path("v2/organizations/{organizationId}/Users")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response createOrganizationUser(
            @Context KeycloakSession session,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            @PathParam("organizationId") String organizationId,
            String rawRequest
    ) {
        logger.debugf("POST /v2/organizations/%s/Users", organizationId);
        logIncomingRequest("POST", uriInfo, headers, rawRequest);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().createUser(
            scimContext,
            parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.User.class)
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Users")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response listOrganizationUsers(
            @Context KeycloakSession session,
            @PathParam("organizationId") String organizationId,
            @QueryParam("filter") String filter,
            @QueryParam("startIndex") @DefaultValue("1") Integer startIndex,
            @QueryParam("count") @DefaultValue("100") Integer count
    ) {
        logger.debugf("GET /v2/organizations/%s/Users filter=%s startIndex=%d count=%d", organizationId, filter, startIndex, count);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        ScimFilter scimFilter;
        try {
            scimFilter = parseFilter(filter);
        } catch (Exception e) {
            logger.warn(String.format("Failed to parse filter: '%s'", filter), e);
            return ScimErrors.invalidFilter("Invalid filter");
        }

        return getOrganizationScimServer().listUsers(
            scimContext,
            scimFilter,
            startIndex,
            count
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Users/{id}")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response findOrganizationUser(
            @Context KeycloakSession session,
            @PathParam("id") String userId,
            @PathParam("organizationId") String organizationId
    ) {
        logger.debugf("GET /v2/organizations/%s/Users/%s", organizationId, userId);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().findUser(
            scimContext,
            userId
        );
    }

    @PUT
    @Path("v2/organizations/{organizationId}/Users/{id}")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response updateOrganizationUser(
            @Context KeycloakSession session,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            @PathParam("id") String userId,
            @PathParam("organizationId") String organizationId,
            String rawRequest
    ) {
        logger.debugf("PUT /v2/organizations/%s/Users/%s", organizationId, userId);
        logIncomingRequest("PUT", uriInfo, headers, rawRequest);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().updateUser(
            scimContext,
            userId,
            parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.User.class)
        );
    }

    @PATCH
    @Path("v2/organizations/{organizationId}/Users/{id}")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response patchOrganizationUser(
            @Context KeycloakSession session,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            @PathParam("id") String userId,
            @PathParam("organizationId") String organizationId,
            String rawRequest
    ) {
        logger.debugf("PATCH /v2/organizations/%s/Users/%s", organizationId, userId);
        logIncomingRequest("PATCH", uriInfo, headers, rawRequest);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().patchUser(
                scimContext,
                userId,
                parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.PatchRequest.class)
        );
    }

    @DELETE
    @Path("v2/organizations/{organizationId}/Users/{id}")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response deleteOrganizationUser(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @PathParam("id") String userId
    ) {
        logger.debugf("DELETE /v2/organizations/%s/Users/%s", organizationId, userId);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().deleteUser(scimContext, userId);
    }

    @POST
    @Path("v2/organizations/{organizationId}/Groups")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response createOrganizationGroup(
        @Context KeycloakSession session,
        @Context HttpHeaders headers,
        @Context UriInfo uriInfo,
        @PathParam("organizationId") String organizationId,
        String rawRequest
    ) {
        logger.debugf("POST /v2/organizations/%s/Groups", organizationId);
        logIncomingRequest("POST", uriInfo, headers, rawRequest);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().createGroup(
            scimContext,
            parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.Group.class)
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Groups")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response listOrganizationGroups(
            @Context KeycloakSession session,
            @PathParam("organizationId") String organizationId,
            @QueryParam("filter") String filter,
            @QueryParam("startIndex") @DefaultValue("1") int startIndex,
            @QueryParam("count") @DefaultValue("100") int count
    ) {
        logger.debugf("GET /v2/organizations/%s/Groups filter=%s startIndex=%d count=%d", organizationId, filter, startIndex, count);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        ScimFilter scimFilter;
        try {
            scimFilter = parseFilter(filter);
        } catch (Exception e) {
            logger.warn(String.format("Failed to parse filter: '%s'", filter), e);
            return ScimErrors.invalidFilter("Invalid filter");
        }

        return getOrganizationScimServer().listGroups(
            scimContext,
            scimFilter,
            startIndex,
            count
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Groups/{id}")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response findOrganizationGroup(
            @Context KeycloakSession session,
            @PathParam("organizationId") String organizationId,
            @PathParam("id") String id
    ) {
        logger.debugf("GET /v2/organizations/%s/Groups/%s", organizationId, id);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().findGroup(
            scimContext,
            id
        );
    }

    @PUT
    @Path("v2/organizations/{organizationId}/Groups/{id}")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response updateOrganizationGroup(
            @Context KeycloakSession session,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            @PathParam("id") String id,
            @PathParam("organizationId") String organizationId,
            String rawRequest
    ) {
        logger.debugf("PUT /v2/organizations/%s/Groups/%s", organizationId, id);
        logIncomingRequest("PUT", uriInfo, headers, rawRequest);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().updateGroup(
            scimContext,
            id,
            parseBody(rawRequest, Group.class)
        );
    }

    @PATCH
    @Path("v2/organizations/{organizationId}/Groups/{id}")
    @Consumes({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response patchOrganizationGroup(
            @Context KeycloakSession session,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            @PathParam("id") String groupId,
            @PathParam("organizationId") String organizationId,
            String rawRequest
    ) {
        logger.debugf("PATCH /v2/organizations/%s/Groups/%s", organizationId, groupId);
        logIncomingRequest("PATCH", uriInfo, headers, rawRequest);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().patchGroup(
                scimContext,
                groupId,
                parseBody(rawRequest, fi.metatavu.keycloak.scim.server.model.PatchRequest.class)
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
        logger.debugf("DELETE /v2/organizations/%s/Groups/%s", organizationId, id);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().deleteGroup(
            scimContext,
            id
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/ResourceTypes")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response listOrganizationResourceTypes(
        @Context KeycloakSession session,
        @Context UriInfo uriInfo,
        @PathParam("organizationId") String organizationId
    ) {
        logger.debugf("GET /v2/organizations/%s/ResourceTypes", organizationId);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().listResourceTypes(
            scimContext
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/ResourceTypes/{id}")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response findOrganizationResourceType(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @PathParam("id") String id
    ) {
        logger.debugf("GET /v2/organizations/%s/ResourceTypes/%s", organizationId, id);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().findResourceType(
            scimContext,
            id
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Schemas")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response listOrganizationSchemas(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @Context UriInfo uriInfo
    ) {
        logger.debugf("GET /v2/organizations/%s/Schemas", organizationId);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().listSchemas(
            scimContext
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/Schemas/{id}")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response findOrganizationSchema(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @PathParam("id") String id
    ) {
        logger.debugf("GET /v2/organizations/%s/Schemas/%s", organizationId, id);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);

        return getOrganizationScimServer().findSchema(
            scimContext,
            id
        );
    }

    @GET
    @Path("v2/organizations/{organizationId}/ServiceProviderConfig")
    @Produces({ContentTypes.APPLICATION_SCIM_JSON, ContentTypes.APPLICATION_SCIM, ContentTypes.APPLICATION_JSON})
    @SuppressWarnings("unused")
    public Response getOrganizationServiceProviderConfig(
        @Context KeycloakSession session,
        @PathParam("organizationId") String organizationId,
        @Context UriInfo uriInfo
    ) {
        logger.debugf("GET /v2/organizations/%s/ServiceProviderConfig", organizationId);
        OrganizationScimContext scimContext = getOrganizationScimServer().getScimContext(session, organizationId);
        getOrganizationScimServer().verifyPermissions(scimContext);
        return getOrganizationScimServer().getServiceProviderConfig(scimContext);
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

    private <T> T parseBody(String rawRequest, Class<T> type) {
        if (rawRequest == null || rawRequest.isBlank()) {
            throw new BadRequestException("Missing request body");
        }

        try {
            return JsonSerialization.readValue(rawRequest, type);
        } catch (IOException e) {
            throw new BadRequestException("Invalid SCIM request payload", e);
        }
    }

    private void logIncomingRequest(String method, UriInfo uriInfo, HttpHeaders headers, String requestBody) {
        if (!isEnabled(LOG_REQUESTS_ENV)) {
            return;
        }

        boolean logSensitive = isEnabled(LOG_SENSITIVE_ENV);
        StringBuilder builder = new StringBuilder();
        builder.append("Incoming SCIM request as curl: curl -X ").append(method)
            .append(" '").append(uriInfo.getRequestUri()).append("'");

        for (Map.Entry<String, List<String>> headerEntry : headers.getRequestHeaders().entrySet()) {
            String headerName = headerEntry.getKey();
            if (headerName == null || headerEntry.getValue() == null) {
                continue;
            }

            if (!logSensitive && isSensitiveHeader(headerName)) {
                builder.append(" -H '").append(escapeShell(headerName)).append(": <redacted>'");
                continue;
            }

            for (String headerValue : headerEntry.getValue()) {
                builder.append(" -H '")
                    .append(escapeShell(headerName))
                    .append(": ")
                    .append(escapeShell(headerValue))
                    .append("'");
            }
        }

        if (requestBody != null && !requestBody.isBlank()) {
            builder.append(" --data-raw '").append(escapeShell(requestBody)).append("'");
        }

        logger.info(builder.toString());
    }

    private boolean isEnabled(String environmentVariable) {
        String value = System.getenv(environmentVariable);
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    private boolean isSensitiveHeader(String headerName) {
        return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(headerName) || "x-api-key".equalsIgnoreCase(headerName);
    }

    private String escapeShell(String value) {
        return value.replace("'", "'\"'\"'");
    }

}

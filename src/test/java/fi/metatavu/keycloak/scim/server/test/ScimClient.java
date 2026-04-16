package fi.metatavu.keycloak.scim.server.test;

import fi.metatavu.keycloak.scim.server.test.client.ApiClient;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.api.GroupsApi;
import fi.metatavu.keycloak.scim.server.test.client.api.MetadataApi;
import fi.metatavu.keycloak.scim.server.test.client.api.UsersApi;
import fi.metatavu.keycloak.scim.server.test.client.model.*;

import java.net.URI;
import java.util.Base64;

/**
 * SCIM client
 */
public class ScimClient {

    private final URI scimUri;
    private final String authorizationHeader;

    /**
     * Constructor for Bearer token authentication
     *
     * @param scimUri SCIM URI
     * @param accessToken access token
     */
    public ScimClient(
        URI scimUri,
        String accessToken
    ) {
        this.scimUri = scimUri;
        this.authorizationHeader = "Bearer " + accessToken;
    }

    /**
     * Constructor for Basic authentication
     *
     * @param scimUri SCIM URI
     * @param username username
     * @param password password
     */
    public ScimClient(
        URI scimUri,
        String username,
        String password
    ) {
        this.scimUri = scimUri;
        this.authorizationHeader = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    /**
     * Lists users
     *
     * @param filter filter
     * @param startIndex start index
     * @param count count
     * @return users list
     * @throws ApiException thrown when API call fails
     */
    public UsersList listUsers(String filter, Integer startIndex, Integer count) throws ApiException {
        return getUsersApi().listUsers(filter, startIndex, count);
    }

    /**
     * Creates a user
     *
     * @param user user to create
     * @return created user
     */
    public User createUser(User user) throws ApiException {
        return getUsersApi().createUser(user);
    }

    /**
     * Finds a user
     *
     * @param id user ID
     * @return found user
     */
    public User findUser(String id) throws ApiException {
        return getUsersApi().findUser(id);
    }

    /**
     * Updates a user
     *
     * @param id user ID
     * @param user user to update
     * @return updated user
     * @throws ApiException thrown when API call fails
     */
    public User updateUser(String id, User user) throws ApiException {
        return getUsersApi().updateUser(id, user);
    }


    /**
     * Patches a user
     *
     * @param id user ID
     * @param patchRequest user to patch
     * @return patched user
     */
    public User patchUser(String id, PatchRequest patchRequest) throws ApiException {
        return getUsersApi().patchUser(id, patchRequest);
    }

    /**
     * Deletes a user
     *
     * @param userId user ID
     */
    public void deleteUser(String userId) throws ApiException {
        getUsersApi().deleteUser(userId);
    }

    /**
     * Lists groups
     *
     * @param filter filter
     * @param startIndex start index
     * @param count count
     * @return groups list
     * @throws ApiException thrown when API call fails
     */
    public GroupsList listGroups(String filter, Integer startIndex, Integer count) throws ApiException {
        return getGroupsApi().listGroups(filter, startIndex, count);
    }

    /**
     * Creates a group
     *
     * @param group group to create
     * @return created group
     * @throws ApiException thrown when API call fails
     */
    public Group createGroup(Group group) throws ApiException {
        return getGroupsApi().createGroup(group);
    }

    /**
     * Finds a group
     *
     * @param id group ID
     * @return found group
     * @throws ApiException thrown when API call fails
     */
    public Group findGroup(String id) throws ApiException {
        return getGroupsApi().getGroup(id);
    }

    /**
     * Updates a group
     *
     * @param id group ID
     * @param group group to update
     * @return updated group
     * @throws ApiException thrown when API call fails
     */
    public Group updateGroup(String id, Group group) throws ApiException {
        return getGroupsApi().updateGroup(id, group);
    }

    /**
     * Patches a group
     *
     * @param id group ID
     * @param patchRequest patch request
     * @return patched group
     * @throws ApiException thrown when API call fails
     */
    public Group patchGroup(String id, PatchRequest patchRequest) throws ApiException {
        return getGroupsApi().patchGroup(id, patchRequest);
    }

    /**
     * Deletes a group
     *
     * @param groupId group ID
     * @throws ApiException thrown when API call fails
     */
    public void deleteGroup(String groupId) throws ApiException {
        getGroupsApi().deleteGroup(groupId);
    }

    /**
     * Lists resource types
     *
     * @return resource types
     */
    public ResourceTypeListResponse getResourceTypes() throws ApiException {
        return getMetadataApi().listResourceTypes();
    }

    /**
     * Finds a resource type
     *
     * @param id resource type ID
     * @return found resource type
     */
    public ResourceType findResourceType(String id) throws ApiException {
        return getMetadataApi().getResourceType(id);
    }

    /**
     * Lists schemas
     *
     * @return schemas
     */
    public SchemaListResponse getSchemas() throws ApiException {
        return getMetadataApi().listSchemas();
    }

    /**
     * Finds a schema
     *
     * @param id schema ID
     * @return found schema
     */
    public SchemaListItem findSchema(String id) throws ApiException {
        return getMetadataApi().getSchema(id);
    }

    /**
     * Returns service provider config
     *
     * @return service provider config
     */
    public ServiceProviderConfig getServiceProviderConfig() throws ApiException {
        return getMetadataApi().getServiceProviderConfig();
    }

    /**
     * Returns initialized users API
     *
     * @return initialized users API
     */
    private UsersApi getUsersApi() {
        return new UsersApi(getApiClient());
    }

    /**
     * Returns initialized groups API
     *
     * @return initialized groups API
     */
    private GroupsApi getGroupsApi() {
        return new GroupsApi(getApiClient());
    }

    private MetadataApi getMetadataApi() {
        return new MetadataApi(getApiClient());
    }

    /**
     * Returns initialized API client
     *
     * @return initialized API client
     */
    private ApiClient getApiClient() {
        ApiClient result = new ApiClient();
        result.setBasePath(scimUri.getPath());
        result.setHost(scimUri.getHost());
        result.setScheme(scimUri.getScheme());
        result.setPort(scimUri.getPort());
        result.setRequestInterceptor(builder -> builder.header("Authorization", authorizationHeader));
        return result;
    }
}
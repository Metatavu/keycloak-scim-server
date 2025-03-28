package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.test.client.ApiClient;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.api.MetadataApi;
import fi.metatavu.keycloak.scim.server.test.client.api.UsersApi;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.ResourceTypeListResponse;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UsersList;

import java.net.URI;

/**
 * SCIM client
 */
public class ScimClient {

    private final URI scimUri;
    private final String accessToken;

    /**
     * Constructor
     *
     * @param scimUri SCIM URI
     * @param accessToken access token
     */
    public ScimClient(
        URI scimUri,
        String accessToken
    ) {
        this.scimUri = scimUri;
        this.accessToken = accessToken;
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
     * Lists resource types
     *
     * @return resource types
     */
    public ResourceTypeListResponse getResourceTypes() throws ApiException {
        return getMetadataApi().listResourceTypes();
    }

    /**
     * Returns initialized users API
     *
     * @return initialized users API
     */
    private UsersApi getUsersApi() {
        return new UsersApi(getApiClient());
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
        result.setRequestInterceptor(builder -> builder.header("Authorization", "Bearer " + accessToken));
        return result;
    }

}
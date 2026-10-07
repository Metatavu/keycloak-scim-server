package fi.metatavu.keycloak.scim.server.test;

import fi.metatavu.keycloak.scim.server.test.client.ApiClient;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.api.GroupsApi;
import fi.metatavu.keycloak.scim.server.test.client.api.MetadataApi;
import fi.metatavu.keycloak.scim.server.test.client.api.UsersApi;
import fi.metatavu.keycloak.scim.server.test.client.model.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SCIM client
 */
public class ScimClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        return listGroups(filter, startIndex, count, null, null);
    }

    /**
     * Lists groups, selecting which attributes the response should carry
     *
     * @param filter filter
     * @param startIndex start index
     * @param count count
     * @param attributes attributes to return, overriding the default set
     * @param excludedAttributes attributes to omit from the default set
     * @return groups list
     * @throws ApiException thrown when API call fails
     */
    public GroupsList listGroups(
            String filter,
            Integer startIndex,
            Integer count,
            String attributes,
            String excludedAttributes
    ) throws ApiException {
        return getGroupsApi().listGroups(filter, startIndex, count, attributes, excludedAttributes);
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
        return findGroup(id, null, null);
    }

    /**
     * Finds a group, selecting which attributes the response should carry
     *
     * @param id group ID
     * @param attributes attributes to return, overriding the default set
     * @param excludedAttributes attributes to omit from the default set
     * @return found group
     * @throws ApiException thrown when API call fails
     */
    public Group findGroup(String id, String attributes, String excludedAttributes) throws ApiException {
        return getGroupsApi().getGroup(id, attributes, excludedAttributes);
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
     * Patches a group.
     *
     * <p>Returns the raw response rather than a parsed group: a successful
     * group PATCH answers 204 No Content (RFC 7644 §3.5.2), so there is
     * nothing to deserialise. Use {@link #patchGroup(String, PatchRequest, String)}
     * when the test needs the updated resource back.
     *
     * @param id group ID
     * @param patchRequest patch request
     * @return raw SCIM response
     * @throws ApiException thrown when API call fails
     */
    public ScimResponse patchGroup(String id, PatchRequest patchRequest) throws ApiException {
        return request("PATCH", String.format("Groups/%s", id), null, patchRequest);
    }

    /**
     * Patches a group and asks for the updated resource back.
     *
     * <p>Supplying "attributes" obliges the server to answer 200 with a body
     * (RFC 7644 §3.5.2) carrying the minimum attribute set plus the attributes
     * named.
     *
     * @param id group ID
     * @param patchRequest patch request
     * @param attributes comma-separated attribute names to return
     * @return patched group
     * @throws ApiException thrown when API call fails
     */
    public Group patchGroup(String id, PatchRequest patchRequest, String attributes) throws ApiException {
        return getGroupsApi().patchGroup(id, patchRequest, attributes);
    }

    /**
     * Finds a group and returns the response unparsed.
     *
     * <p>Lets a test assert on the literal JSON — specifically that an
     * excluded attribute is absent rather than serialised as null, which RFC
     * 7643 §2.5 would read as "this group has no members".
     *
     * @param id group ID
     * @param query raw query string, without the leading "?", or null
     * @return raw SCIM response
     * @throws ApiException thrown when API call fails
     */
    public ScimResponse findGroupRaw(String id, String query) throws ApiException {
        return request("GET", String.format("Groups/%s", id), query, null);
    }

    /**
     * Lists groups and returns the response unparsed.
     *
     * @param query raw query string, without the leading "?", or null
     * @return raw SCIM response
     * @throws ApiException thrown when API call fails
     */
    public ScimResponse listGroupsRaw(String query) throws ApiException {
        return request("GET", "Groups", query, null);
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
    /**
     * Issues a SCIM request without the generated client, so that the status
     * code and the response body can be asserted exactly as the server sent
     * them.
     *
     * @param method HTTP method
     * @param path path relative to the SCIM base URI
     * @param query raw query string without the leading "?", or null
     * @param body request body to serialise, or null
     * @return raw SCIM response
     * @throws ApiException thrown when the request cannot be made
     */
    private ScimResponse request(String method, String path, String query, Object body) throws ApiException {
        try {
            String base = scimUri.toString();
            if (!base.endsWith("/")) {
                base = base + "/";
            }

            URI uri = URI.create(base + path + (query == null || query.isBlank() ? "" : "?" + query));

            HttpRequest.BodyPublisher bodyPublisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", authorizationHeader)
                    .header("Accept", "application/scim+json")
                    .header("Content-Type", "application/scim+json")
                    .method(method, bodyPublisher)
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            // Mirror the generated client: a non-2xx surfaces as ApiException
            // carrying the status and the SCIM Error body, so error assertions
            // work the same whichever path a test took to get here.
            if (response.statusCode() / 100 != 2) {
                throw new ApiException(
                        response.statusCode(),
                        String.format("%s %s call failed", method, path),
                        response.headers(),
                        response.body()
                );
            }

            return new ScimResponse(response.statusCode(), response.body());
        } catch (JsonProcessingException e) {
            throw new ApiException(e);
        } catch (IOException e) {
            throw new ApiException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(e);
        }
    }

    /**
     * A SCIM response as it came off the wire.
     *
     * @param status HTTP status code
     * @param body response body, empty when the server returned no content
     */
    public record ScimResponse(int status, String body) {

        /**
         * Whether the body contains the given attribute as a JSON property.
         *
         * @param attributeName attribute name
         * @return true if the property is present
         */
        public boolean hasAttribute(String attributeName) {
            return body != null && body.contains("\"" + attributeName + "\"");
        }

        /**
         * Size of the response body in bytes.
         *
         * @return byte count
         */
        public int size() {
            return body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
        }
    }

    private ApiClient getApiClient() {
        ApiClient result = new ApiClient();
        String path = scimUri.getPath();
        result.setBasePath(path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
        result.setHost(scimUri.getHost());
        result.setScheme(scimUri.getScheme());
        result.setPort(scimUri.getPort());
        result.setRequestInterceptor(builder -> builder.header("Authorization", authorizationHeader));
        return result;
    }
}
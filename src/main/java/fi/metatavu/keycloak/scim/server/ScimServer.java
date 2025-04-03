package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.KeycloakSession;

/**
 * * SCIM server interface
 *
 * @param <T> SCIM context type
 */
public interface ScimServer <T extends ScimContext> {

    /**
     * Creates a new user
     *
     * @param scimContext SCIM context
     * @param user        user to create
     * @return response
     */
    Response createUser(
        T scimContext,
        fi.metatavu.keycloak.scim.server.model.User user
    );

    /**
     * Lists users
     *
     * @param scimContext SCIM context
     * @param scimFilter  SCIM filter
     * @param startIndex  start index
     * @param count       count
     * @return response
     */
    Response listUsers(
        T scimContext,
        ScimFilter scimFilter,
        Integer startIndex,
        Integer count
    );

    /**
     * Finds a user by ID
     *
     * @param scimContext SCIM context
     * @param userId      user ID
     * @return response
     */
    Response findUser(T scimContext, String userId);

    /**
     * Updates a user
     *
     * @param scimContext SCIM context
     * @param userId      user ID
     * @param body        user data
     * @return response
     */
    Response updateUser(T scimContext, String userId, fi.metatavu.keycloak.scim.server.model.User body);

    /**
     * Patches a user
     *
     * @param scimContext SCIM context
     * @param userId      user ID
     * @param patchRequest patch request
     * @return response
     */
    Response patchUser(T scimContext, String userId, fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest);

    /**
     * Deletes a user
     *
     * @param scimContext SCIM context
     * @param userId      user ID
     * @return response
     */
    Response deleteUser(T scimContext, String userId);

    /**
     * Creates a new group
     *
     * @param scimContext SCIM context
     * @param createRequest group to create
     * @return response
     */
    Response createGroup(T scimContext, fi.metatavu.keycloak.scim.server.model.Group createRequest);

    /**
     * Lists groups
     *
     * @param scimContext SCIM context
     * @param startIndex  start index
     * @param count       count
     * @return response
     */
    Response listGroups(T scimContext, int startIndex, int count);

    /**
     * Finds a group by ID
     *
     * @param scimContext SCIM context
     * @param id          group ID
     * @return response
     */
    Response findGroup(T scimContext, String id);

    /**
     * Updates a group
     *
     * @param scimContext SCIM context
     * @param id          group ID
     * @param updateRequest group data
     * @return response
     */
    Response updateGroup(T scimContext, String id, fi.metatavu.keycloak.scim.server.model.Group updateRequest);

    /**
     * Patches a group
     *
     * @param scimContext SCIM context
     * @param groupId     group ID
     * @param patchRequest patch request
     * @return response
     */
    Response patchGroup(T scimContext, String groupId, fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest);

    /**
     * Deletes a group
     *
     * @param scimContext SCIM context
     * @param id          group ID
     * @return response
     */
    Response deleteGroup(T scimContext, String id);

    /**
     * Lists resource types
     *
     * @param scimContext SCIM context
     * @return response
     */
    Response listResourceTypes(T scimContext);

    /**
     * Finds a resource type by ID
     *
     * @param scimContext SCIM context
     * @param id          resource type ID
     * @return response
     */
    Response findResourceType(T scimContext, String id);

    /**
     * Lists schemas
     *
     * @param scimContext SCIM context
     * @return response
     */
    Response listSchemas(T scimContext);

    /**
     * Finds a schema by ID
     *
     * @param scimContext SCIM context
     * @param id          schema ID
     * @return response
     */
    Response findSchema(T scimContext, String id);

    /**
     * Gets service provider configuration
     *
     * @param scimContext SCIM context
     * @return response
     */
    Response getServiceProviderConfig(T scimContext);

    /**
     * Verifies permissions
     *
     * @param scimContext SCIM context
     */
    void verifyPermissions(T scimContext);
}

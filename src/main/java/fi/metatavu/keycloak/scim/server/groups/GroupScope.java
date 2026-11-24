package fi.metatavu.keycloak.scim.server.groups;

import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.GroupModel;
import org.keycloak.models.UserModel;

import java.util.stream.Stream;

/**
 * The set of Keycloak groups a SCIM endpoint manages: realm groups for the realm endpoint,
 * the groups of one organization for an organization endpoint.
 */
public interface GroupScope {

    /**
     * Creates a group within the scope
     *
     * @param displayName group name
     * @return created group
     * @throws org.keycloak.models.ModelDuplicateException when a sibling group already has the name
     */
    GroupModel createGroup(String displayName);

    /**
     * Finds a group within the scope
     *
     * @param groupId group ID
     * @return group, or null if the group does not exist or lies outside the scope
     */
    GroupModel findGroup(String groupId);

    /**
     * Lists every group within the scope
     *
     * @return groups
     */
    Stream<GroupModel> getGroupsStream();

    /**
     * Lists the groups within the scope whose name equals the given one
     *
     * @param displayName group name
     * @return matching groups
     */
    Stream<GroupModel> getGroupsByNameStream(String displayName);

    /**
     * Whether the user may be a member of the scope's groups, on top of being SCIM-managed
     *
     * @param user user
     * @return true if the user may be a member
     */
    boolean canBeMember(UserModel user);

    /**
     * Admin event resource type for changes to a group of the scope
     *
     * @return resource type
     */
    ResourceType getGroupResourceType();

    /**
     * Admin event resource type for membership changes in a group of the scope
     *
     * @return resource type
     */
    ResourceType getGroupMembershipResourceType();

    /**
     * Admin event resource path of a group, in the form the Keycloak admin API uses for it
     *
     * @param group group
     * @return resource path
     */
    String getGroupResourcePath(GroupModel group);

}

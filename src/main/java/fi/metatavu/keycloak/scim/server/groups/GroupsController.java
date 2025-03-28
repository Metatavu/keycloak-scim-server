package fi.metatavu.keycloak.scim.server.groups;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.consts.Schemas;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.GroupMembersInner;
import fi.metatavu.keycloak.scim.server.model.GroupsList;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Groups controller
 */
public class GroupsController extends AbstractController {

    /**
     * Creates a group
     *
     * @param scimContext SCIM context
     * @param scimGroup SCIM group
     * @return created group
     */
    public Group createGroup(
            ScimContext scimContext,
            Group scimGroup
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        GroupModel group = session.groups().createGroup(realm, scimGroup.getDisplayName());

        if (scimGroup.getMembers() != null) {
            for (GroupMembersInner member : scimGroup.getMembers()) {
                UserModel user = session.users().getUserById(realm, member.getValue());
                if (user != null) {
                    user.joinGroup(group);
                }
            }
        }

        return translateGroup(scimContext, group);
    }

    /**
     * Finds a group
     *
     * @param scimContext SCIM context
     * @param groupId group ID
     * @return found group
     */
    public Group findGroup(
            ScimContext scimContext,
            String groupId
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();
        GroupModel group = session.groups().getGroupById(realm, groupId);
        if (group == null) {
            return null;
        }

        return translateGroup(scimContext, group);
    }

    /**
     * Lists groups
     *
     * @param scimContext SCIM context
     * @param startIndex start index
     * @param count count
     * @return groups list
     */
    public GroupsList listGroups(
            ScimContext scimContext,
            int startIndex,
            int count
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        List<GroupModel> allGroups = session.groups()
            .getGroupsStream(realm)
            .toList();

        List<Group> groups = allGroups.stream()
            .skip(startIndex)
            .limit(count)
            .map(group -> translateGroup(scimContext, group))
            .collect(Collectors.toList());

        GroupsList result = new GroupsList();
        result.setTotalResults(allGroups.size());
        result.setStartIndex(startIndex);
        result.setItemsPerPage(count);
        result.setResources(groups);
        result.setSchemas(Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse"));

        return result;
    }

    /**
     * Updates a group
     *
     * @param scimContext SCIM context
     * @param existing existing group
     * @param group SCIM group
     * @return updated group
     */
    public Group updateGroup(ScimContext scimContext, GroupModel existing, fi.metatavu.keycloak.scim.server.model.Group group) {
        existing.setName(group.getDisplayName());
        return translateGroup(scimContext, existing);
    }

    /**
     * Deletes a group
     *
     * @param scimContext SCIM context
     * @param group group
     */
    public void deleteGroup(ScimContext scimContext, GroupModel group) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();
        session.groups().removeGroup(realm, group);
    }

    /**
     * Translates Keycloak group to SCIM group
     *
     * @param group group
     * @return SCIM group
     */
    private Group translateGroup(
            ScimContext scimContext,
            GroupModel group
    ) {
        RealmModel realm = scimContext.getRealm();
        KeycloakSession session = scimContext.getSession();

        List<GroupMembersInner> members = session.users().getGroupMembersStream(realm, group)
                .map(member -> new GroupMembersInner()
                        .value(member.getId())
                        .display(member.getUsername())
                )
                .toList();

        return new Group()
                .id(group.getId())
                .displayName(group.getName())
                .members(members)
                .schemas(Collections.singletonList(Schemas.GROUP_SCHEMA))
                .meta(getMeta(scimContext, "Group", String.format("Groups/%s", group.getId())));
    }
}

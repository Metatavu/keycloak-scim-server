package fi.metatavu.keycloak.scim.server.groups;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.patch.PatchOperation;
import fi.metatavu.keycloak.scim.server.consts.Schemas;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.GroupMembersInner;
import fi.metatavu.keycloak.scim.server.model.GroupsList;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import org.jboss.logging.Logger;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Groups controller
 */
public class GroupsController extends AbstractController {

    private static final Logger logger = Logger.getLogger(GroupsController.class);

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
     * Patch group with SCIM group data
     *
     * @param scimContext SCIM context
     * @param existing existing group
     * @param patchRequest patch request
     * @return patched group
     */
    public fi.metatavu.keycloak.scim.server.model.Group patchGroup(
            ScimContext scimContext,
            GroupModel existing,
            fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest
    ) throws UnsupportedGroupPath, UnsupportedPatchOperation {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        for (var operation : patchRequest.getOperations()) {
            PatchOperation op = PatchOperation.fromString(operation.getOp());
            String path = operation.getPath();
            Object value = operation.getValue();

            if (op == null) {
                logger.warn("Invalid patch operation: " + operation.getOp());
                throw new UnsupportedPatchOperation("Unsupported patch operation: " + operation.getOp());
            }

            if (value == null) {
                logger.warn("Value is null for patch operation: " + op);
                break;
            }

            GroupPath groupPath = GroupPath.findByPath(operation.getPath());
            if (groupPath == null) {
                throw new UnsupportedGroupPath("Unsupported patch path: " + path);
            }

            switch (op) {
                case REPLACE, ADD -> {
                    switch (groupPath) {
                        case DISPLAY_NAME -> existing.setName((String) value);
                        case MEMBERS -> {
                            // Clear current members if REPLACE, just add if ADD
                            if (op == PatchOperation.REPLACE) {
                                session.users().getGroupMembersStream(realm, existing)
                                    .forEach(user -> user.leaveGroup(existing));
                            }

                            for (Object obj : (List<?>) value) {
                                if (!(obj instanceof Map<?, ?> memberMap)) {
                                    logger.warn("Invalid member object: " + obj);
                                    continue;
                                }

                                String memberId = (String) memberMap.get("value");
                                if (memberId == null) {
                                    logger.warn("Member value missing: " + obj);
                                    continue;
                                }

                                UserModel user = scimContext.getSession().users().getUserById(scimContext.getRealm(), memberId);
                                if (user != null) {
                                    user.joinGroup(existing);
                                }
                            }
                        }
                    }
                }

                case REMOVE -> {
                    switch (groupPath) {
                        case DISPLAY_NAME -> existing.setName(null);
                        case MEMBERS -> {
                            for (Object obj : (List<?>) value) {
                                if (!(obj instanceof Map<?, ?> memberMap)) {
                                    logger.warn("Invalid member object: " + obj);
                                    continue;
                                }

                                String memberId = (String) memberMap.get("value");
                                if (memberId == null) {
                                    logger.warn("Member value missing: " + obj);
                                    continue;
                                }

                                UserModel user = scimContext.getSession().users().getUserById(scimContext.getRealm(), memberId);
                                if (user != null) {
                                    user.leaveGroup(existing);
                                }
                            }
                        }
                    }
                }
            }
        }

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

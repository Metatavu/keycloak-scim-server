package fi.metatavu.keycloak.scim.server.groups;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.adminEvents.AdminEventController;
import fi.metatavu.keycloak.scim.server.filter.ComparisonFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.metadata.GroupAttribute;
import fi.metatavu.keycloak.scim.server.patch.PatchOperation;
import fi.metatavu.keycloak.scim.server.consts.Schemas;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.GroupMembersInner;
import fi.metatavu.keycloak.scim.server.model.GroupsList;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import org.jboss.logging.Logger;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Groups controller
 */
public class GroupsController extends AbstractController {

    private static final Logger logger = Logger.getLogger(GroupsController.class);
    private final AdminEventController adminEventController = new AdminEventController();

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

        dispatchGroupCreateEvent(scimContext, group);

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
            ScimFilter scimFilter,
            int startIndex,
            int count
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();
        GroupsList result = new GroupsList();

        Map<String, String> searchParams = new HashMap<>();

        // For now only support to filter on display name
        List<GroupModel> filteredGroups;
        if(scimFilter instanceof ComparisonFilter(
                String attribute, ScimFilter.Operator operator, String value
        ) && operator == ScimFilter.Operator.EQ && attribute.equals(GroupAttribute.DISPLAY_NAME.getScimPath())){
            filteredGroups = session.groups().searchForGroupByNameStream(realm, value, true, startIndex, count).toList();
        }else{
            filteredGroups = session.groups().getGroupsStream(realm).toList();
        }

        List<Group> groups = filteredGroups.stream()
            .skip(startIndex)
            .limit(count)
            .map(group -> translateGroup(scimContext, group))
            .collect(Collectors.toList());

        result.setTotalResults(filteredGroups.size());
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

            // RFC 7644 §3.5.2: when "path" is omitted, "value" carries a
            // partial resource (map of attribute -> value) to apply to the
            // target. Okta emits this for group metadata reconciliation:
            // {"op":"replace","value":{"id":"...","displayName":"..."}}.
            // Iterate the map and apply each known attribute; ignore
            // read-only / unknown keys so spurious metadata fields like "id"
            // don't fail the request.
            if (path == null) {
                if (!(value instanceof Map<?, ?> valueMap)) {
                    throw new UnsupportedGroupPath("PatchOp without 'path' requires a map-valued 'value'");
                }
                for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
                    String attrName = String.valueOf(entry.getKey());
                    GroupAttribute attr = GroupAttribute.findByScimPath(attrName);
                    if (attr == null) {
                        logger.debugf("Ignoring unsupported group attribute in path-less PATCH: %s", attrName);
                        continue;
                    }
                    applyGroupPatchValue(scimContext, op, attr, null, existing, entry.getValue());
                }
                continue;
            }

            // Extract base attribute path (e.g., "members" from "members[value eq \"id\"]")
            String attributePath = path.contains("[")
                ? path.substring(0, path.indexOf("["))
                : path;

            GroupAttribute groupAttribute = GroupAttribute.findByScimPath(attributePath);
            if (groupAttribute == null) {
                throw new UnsupportedGroupPath("Unsupported patch path: " + path);
            }

            // Value can be null for REMOVE operations with path filters
            if (value == null && op != PatchOperation.REMOVE) {
                logger.warn("Value is null for patch operation: " + op);
                break;
            }

            applyGroupPatchValue(scimContext, op, groupAttribute, path, existing, value);
        }

        return translateGroup(scimContext, existing);
    }

    /**
     * Applies a single PATCH operation to a group attribute. Shared by the
     * path-based and path-less PATCH branches so the membership and
     * displayName mutation logic stays in one place.
     */
    private void applyGroupPatchValue(
        ScimContext scimContext,
        PatchOperation op,
        GroupAttribute groupAttribute,
        String path,
        GroupModel existing,
        Object value
    ) {
        switch (op) {
            case REPLACE, ADD -> applyAddOrReplace(scimContext, op, groupAttribute, existing, value);
            case REMOVE -> applyRemove(scimContext, groupAttribute, path, existing, value);
        }
    }

    private void applyAddOrReplace(
        ScimContext scimContext,
        PatchOperation op,
        GroupAttribute groupAttribute,
        GroupModel existing,
        Object value
    ) {
        switch (groupAttribute) {
            case DISPLAY_NAME -> existing.setName((String) value);
            case MEMBERS -> addOrReplaceMembers(scimContext, op, existing, (List<?>) value);
        }
    }

    private void addOrReplaceMembers(
        ScimContext scimContext,
        PatchOperation op,
        GroupModel existing,
        List<?> members
    ) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        if (op == PatchOperation.REPLACE) {
            session.users().getGroupMembersStream(realm, existing)
                .forEach(user -> user.leaveGroup(existing));
        }

        for (Object obj : members) {
            if (!(obj instanceof Map<?, ?> memberMap)) {
                logger.warn("Invalid member object: " + obj);
                continue;
            }
            String memberId = (String) memberMap.get("value");
            if (memberId == null) {
                logger.warn("Member value missing: " + obj);
                continue;
            }
            UserModel user = session.users().getUserById(realm, memberId);
            if (user != null) {
                user.joinGroup(existing);
                dispatchGroupMembershipJoinEvent(scimContext, existing, user);
            }
        }
    }

    private void applyRemove(
        ScimContext scimContext,
        GroupAttribute groupAttribute,
        String path,
        GroupModel existing,
        Object value
    ) {
        switch (groupAttribute) {
            case DISPLAY_NAME -> existing.setName(null);
            case MEMBERS -> removeMembers(scimContext, path, existing, value);
        }
    }

    private void removeMembers(ScimContext scimContext, String path, GroupModel existing, Object value) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        if (path != null && path.contains("[")) {
            removeMemberByFilter(session, realm, scimContext, path, existing);
        } else if (value instanceof List<?> list) {
            removeMembersFromList(session, realm, scimContext, list, existing);
        }
    }

    private void removeMemberByFilter(
        KeycloakSession session, RealmModel realm, ScimContext scimContext,
        String path, GroupModel existing
    ) {
        String memberId = extractValueFromFilter(path);
        if (memberId != null) {
            UserModel user = session.users().getUserById(realm, memberId);
            if (user != null) {
                user.leaveGroup(existing);
                dispatchGroupMembershipLeaveEvent(scimContext, existing, user);
            }
        }
    }

    private void removeMembersFromList(
        KeycloakSession session, RealmModel realm, ScimContext scimContext,
        List<?> list, GroupModel existing
    ) {
        for (Object obj : list) {
            if (obj instanceof Map<?, ?> memberMap) {
                String memberId = (String) memberMap.get("value");
                if (memberId != null) {
                    UserModel user = session.users().getUserById(realm, memberId);
                    if (user != null) {
                        user.leaveGroup(existing);
                        dispatchGroupMembershipLeaveEvent(scimContext, existing, user);
                    }
                }
            }
        }
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

        dispatchGroupDeleteEvent(scimContext, group);
    }

    /**
     * Extracts value from SCIM filter path
     * Example: "members[value eq \"user-id\"]" -> "user-id"
     *
     * @param path path with filter
     * @return extracted value or null
     */
    private String extractValueFromFilter(String path) {
        if (path == null || !path.contains("\"")) {
            return null;
        }

        int firstQuote = path.indexOf("\"");
        int lastQuote = path.lastIndexOf("\"");

        if (firstQuote != -1 && lastQuote > firstQuote) {
            return path.substring(firstQuote + 1, lastQuote);
        }

        return null;
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

    /**
     * Dispatches group create event
     *
     * @param scimContext SCIM context
     * @param group group
     */
    protected void dispatchGroupCreateEvent(
            ScimContext scimContext,
            GroupModel group
    ) {
        GroupRepresentation groupRepresentation = ModelToRepresentation.toRepresentation(group, false);

        adminEventController.sendAdminEvent(
                scimContext,
                OperationType.CREATE,
                ResourceType.GROUP,
                "groups/" + group.getId(),
                groupRepresentation
        );
    }

    /**
     * Dispatches group creation event
     *
     * @param scimContext SCIM context
     * @param group group
     */
    protected void dispatchGroupDeleteEvent(
            ScimContext scimContext,
            GroupModel group
    ) {
        adminEventController.sendAdminEvent(
                scimContext,
                OperationType.DELETE,
                ResourceType.GROUP,
                "groups/" + group.getId(),
                null
        );
    }

    /**
     * Dispatches group membership join event
     *
     * @param scimContext SCIM context
     * @param group group
     * @param user user
     */
    protected void dispatchGroupMembershipJoinEvent(
            ScimContext scimContext,
            GroupModel group,
            UserModel user
    ) {
        GroupRepresentation groupRepresentation = ModelToRepresentation.toRepresentation(group, false);

        adminEventController.sendAdminEvent(
                scimContext,
                OperationType.CREATE,
                ResourceType.GROUP_MEMBERSHIP,
                "groups/" + group.getId() + "/members/" + user.getId(),
                groupRepresentation,
                 Map.of(
                         UserModel.USERNAME, user.getUsername(),
                         UserModel.EMAIL, user.getEmail() == null ? "" : user.getEmail()
                )
        );
    }

    /**
     * Dispatches group membership leave event
     *
     * @param scimContext SCIM context
     * @param group group
     * @param user user
     */
    protected void dispatchGroupMembershipLeaveEvent(
            ScimContext scimContext,
            GroupModel group,
            UserModel user
    ) {
        GroupRepresentation groupRepresentation = ModelToRepresentation.toRepresentation(group, false);

        adminEventController.sendAdminEvent(
                scimContext,
                OperationType.DELETE,
                ResourceType.GROUP_MEMBERSHIP,
                "groups/" + group.getId() + "/members/" + user.getId(),
                groupRepresentation,
                Map.of(
                        UserModel.USERNAME, user.getUsername(),
                        UserModel.EMAIL, user.getEmail() == null ? "" : user.getEmail()
                )
        );
    }
}

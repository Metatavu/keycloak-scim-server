package fi.metatavu.keycloak.scim.server.groups;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.ScimPagination;
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
import org.keycloak.models.cache.UserCache;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Groups controller
 */
public class GroupsController extends AbstractController {

    private static final Logger logger = Logger.getLogger(GroupsController.class);
    private static final String EXTERNAL_ID_ATTRIBUTE = GroupAttribute.EXTERNAL_ID.getScimPath();
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

        applyExternalId(group, getExternalId(scimGroup));

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
     * @param startIndex 1-based SCIM start index
     * @param count count
     * @return groups list
     */
    public GroupsList listGroups(
            ScimContext scimContext,
            ScimFilter scimFilter,
            int startIndex,
            int count
    ) {
        int scimStartIndex = ScimPagination.normalizeStartIndex(startIndex);
        int offset = ScimPagination.toZeroBasedOffset(startIndex);

        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();
        GroupsList result = new GroupsList();

        Map<String, String> searchParams = new HashMap<>();

        // For now only support to filter on display name
        List<GroupModel> filteredGroups;
        List<Group> groups;
        if (scimFilter instanceof ComparisonFilter(
                String attribute, ScimFilter.Operator operator, String value
        ) && operator == ScimFilter.Operator.EQ && attribute.equals(GroupAttribute.DISPLAY_NAME.getScimPath())) {
            filteredGroups = session.groups().searchForGroupByNameStream(realm, value, true, offset, count).toList();
            groups = filteredGroups.stream()
                .map(group -> translateGroup(scimContext, group))
                .collect(Collectors.toList());
        } else {
            filteredGroups = session.groups().getGroupsStream(realm).toList();
            groups = filteredGroups.stream()
                .skip(offset)
                .limit(count)
                .map(group -> translateGroup(scimContext, group))
                .collect(Collectors.toList());
        }

        result.setTotalResults(filteredGroups.size());
        result.setStartIndex(scimStartIndex);
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
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        if (group.getDisplayName() != null) {
            existing.setName(group.getDisplayName());
        }

        applyExternalId(existing, getExternalId(group));

        // SCIM PUT is a full replace of the resource — reconcile members against the request.
        // Okta's Group Push uses PUT (not PATCH) with the desired final member list.
        List<GroupMembersInner> requestedMembers = group.getMembers();
        if (requestedMembers != null) {
            Set<String> desiredIds = requestedMembers.stream()
                    .map(GroupMembersInner::getValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<UserModel> currentMembers = session.users()
                    .getGroupMembersStream(realm, existing)
                    .collect(Collectors.toList());

            Set<String> currentIds = currentMembers.stream()
                    .map(UserModel::getId)
                    .collect(Collectors.toSet());

            UserCache userCache = session.getProvider(UserCache.class);

            // Remove members no longer in the desired set
            for (UserModel user : currentMembers) {
                if (!desiredIds.contains(user.getId())) {
                    user.leaveGroup(existing);
                    if (userCache != null) {
                        userCache.evict(realm, user);
                    }
                    dispatchGroupMembershipLeaveEvent(scimContext, existing, user);
                }
            }

            // Add members that are new
            for (String id : desiredIds) {
                if (currentIds.contains(id)) {
                    continue;
                }
                UserModel user = session.users().getUserById(realm, id);
                if (user != null) {
                    user.joinGroup(existing);
                    if (userCache != null) {
                        userCache.evict(realm, user);
                    }
                    dispatchGroupMembershipJoinEvent(scimContext, existing, user);
                }
            }
        }

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
    ) throws UnsupportedGroupPath, UnsupportedPatchOperation, InvalidGroupMemberReference {
        for (var operation : patchRequest.getOperations()) {
            PatchOperation op = PatchOperation.fromString(operation.getOp());
            String path = operation.getPath();
            Object value = operation.getValue();

            if (op == null) {
                logger.warn("Invalid patch operation: " + operation.getOp());
                throw new UnsupportedPatchOperation("Unsupported patch operation: " + operation.getOp());
            }

            // RFC 7644 §3.5.2: when "path" is omitted, "value" carries a map of
            // attribute -> value to apply to the resource. Okta's Group Push
            // (add/remove members) emits this shape:
            //   {"op":"replace","value":{"members":[{"value":"<user-id>"}]}}
            // Without this branch the code below would call findByScimPath(null),
            // get null, and throw UnsupportedGroupPath, breaking Okta group pushes.
            if (path == null) {
                if (!(value instanceof Map<?, ?> valueMap)) {
                    throw new UnsupportedGroupPath("PatchOp without 'path' requires a map-valued 'value'");
                }
                for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
                    String attrPath = String.valueOf(entry.getKey());
                    if (isReadOnlyOrStructural(attrPath)) {
                        // RFC 7644 §3.5.2 / §7.5: ignore read-only and
                        // structural attributes (id, meta, schemas)
                        // on PATCH. Okta echoes the resource id back inside
                        // 'value' on Group Push.
                        continue;
                    }
                    GroupAttribute attr = GroupAttribute.findByScimPath(attrPath);
                    if (attr == null) {
                        throw new UnsupportedGroupPath("Unsupported attribute: " + attrPath);
                    }
                    applyGroupPatch(scimContext, op, attr, attrPath, entry.getValue(), existing);
                }
                continue;
            }

            // Extract base attribute path (e.g., "members" from "members[value eq \"id\"]")
            String attributePath = path.contains("[")
                ? path.substring(0, path.indexOf("["))
                : path;

            if (isReadOnlyOrStructural(attributePath)) {
                continue;
            }

            GroupAttribute groupAttribute = GroupAttribute.findByScimPath(attributePath);
            if (groupAttribute == null) {
                throw new UnsupportedGroupPath("Unsupported patch path: " + path);
            }

            // Value can be null for REMOVE operations with path filters
            if (value == null && op != PatchOperation.REMOVE) {
                logger.warn("Value is null for patch operation: " + op);
                break;
            }

            // For REMOVE with a path filter (e.g. members[value eq "id"]), extract
            // the member ID from the filter and wrap it in list form so applyGroupPatch
            // can handle it uniformly.
            Object effectiveValue = value;
            if (op == PatchOperation.REMOVE && groupAttribute == GroupAttribute.MEMBERS && path.contains("[")) {
                String memberId = extractValueFromFilter(path);
                if (memberId != null) {
                    effectiveValue = List.of(Map.of("value", memberId));
                } else {
                    throw new UnsupportedGroupPath("Unsupported members filter: " + path);
                }
            }

            applyGroupPatch(scimContext, op, groupAttribute, path, effectiveValue, existing);
        }

        return translateGroup(scimContext, existing);
    }

    /**
     * Apply a single SCIM PatchOp on a Group.
     *
     * <p>Atomicity scope: per operation. If a PatchRequest contains multiple
     * operations, each is applied independently in order. An earlier
     * successful operation is NOT rolled back if a later one fails.
     *
     * <p>For MEMBERS modifications (ADD/REPLACE/REMOVE), every incoming member
     * ID is resolved via {@link #resolveMembers} before any mutation. The
     * first unresolved ID raises {@link InvalidGroupMemberReference}, so the
     * group's membership stays intact for that operation.
     *
     * <p>Used by both the path-less and path-based PatchOp branches of
     * {@link #patchGroup}.
     */
    private void applyGroupPatch(
            ScimContext scimContext,
            PatchOperation op,
            GroupAttribute attr,
            String attrPath,
            Object value,
            GroupModel existing
    ) throws InvalidGroupMemberReference, UnsupportedGroupPath {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        switch (op) {
            case REPLACE, ADD -> {
                switch (attr) {
                    case EXTERNAL_ID -> {
                        if (!(value instanceof String s)) {
                            throw new UnsupportedGroupPath("externalId requires a string value");
                        }
                        applyExternalId(existing, s);
                    }
                    case DISPLAY_NAME -> {
                        if (!(value instanceof String s)) {
                            throw new UnsupportedGroupPath("displayName requires a string value");
                        }
                        existing.setName(s);
                    }
                    case MEMBERS -> {
                        List<UserModel> resolved = resolveMembers(session, realm, value);
                        if (op == PatchOperation.REPLACE) {
                            session.users().getGroupMembersStream(realm, existing)
                                    .forEach(u -> {
                                        u.leaveGroup(existing);
                                        dispatchGroupMembershipLeaveEvent(scimContext, existing, u);
                                    });
                        }
                        for (UserModel u : resolved) {
                            u.joinGroup(existing);
                            dispatchGroupMembershipJoinEvent(scimContext, existing, u);
                        }
                    }
                }
            }
            case REMOVE -> {
                switch (attr) {
                    case EXTERNAL_ID -> existing.removeAttribute(EXTERNAL_ID_ATTRIBUTE);
                    case DISPLAY_NAME -> existing.setName(null);
                    case MEMBERS -> {
                        // REMOVE shares the strict resolution path with REPLACE/ADD: an unknown
                        // member id surfaces as 400 InvalidGroupMemberReference rather than a
                        // silent no-op. SCIM clients with stale state get an actionable error
                        // instead of believing the membership change went through.
                        List<UserModel> resolved = resolveMembers(session, realm, value);
                        for (UserModel u : resolved) {
                            u.leaveGroup(existing);
                            dispatchGroupMembershipLeaveEvent(scimContext, existing, u);
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolve every member-id-shaped entry in {@code value} into a UserModel,
     * failing with {@link InvalidGroupMemberReference} on the first unknown ID
     * before any mutation. Accepts a List of Maps each carrying a "value" key.
     * Returns an empty list for any non-list input (tolerates null/empty values
     * on REMOVE operations).
     *
     * <p>Atomicity scope: within a single operation only. Resolution runs in
     * full before any group membership is modified, so a bad ID aborts the
     * operation without partially applying changes. It does NOT span multiple
     * operations in the same PatchRequest (see {@link #applyGroupPatch}).
     *
     * <p>REMOVE uses this same path: an unknown member ID returns 400 rather
     * than silently no-oping, so SCIM clients with stale state receive an
     * actionable error instead of a false success.
     */
    private List<UserModel> resolveMembers(
            KeycloakSession session,
            RealmModel realm,
            Object value
    ) throws InvalidGroupMemberReference {
        List<UserModel> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return out;
        }
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> memberMap)) {
                continue;
            }
            Object idObj = memberMap.get("value");
            if (!(idObj instanceof String rawMemberId)) {
                continue;
            }
            String memberId = rawMemberId.strip();
            if (memberId.isEmpty()) {
                continue;
            }
            UserModel user = session.users().getUserById(realm, memberId);
            if (user == null) {
                throw new InvalidGroupMemberReference(memberId);
            }
            out.add(user);
        }
        return out;
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
                .externalId(group.getFirstAttribute(EXTERNAL_ID_ATTRIBUTE))
                .displayName(group.getName())
                .members(members)
                .schemas(Collections.singletonList(Schemas.GROUP_SCHEMA))
                .meta(getMeta(scimContext, "Group", String.format("Groups/%s", group.getId())));
    }

    private String getExternalId(fi.metatavu.keycloak.scim.server.model.Group scimGroup) {
        return scimGroup.getExternalId();
    }

    private void applyExternalId(GroupModel group, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            group.removeAttribute(EXTERNAL_ID_ATTRIBUTE);
            return;
        }

        group.setSingleAttribute(EXTERNAL_ID_ATTRIBUTE, externalId);
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

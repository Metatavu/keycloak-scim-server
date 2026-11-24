package fi.metatavu.keycloak.scim.server.groups;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.ScimPagination;
import fi.metatavu.keycloak.scim.server.adminEvents.AdminEventController;
import fi.metatavu.keycloak.scim.server.attributes.AttributeSelection;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.filter.ComparisonFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.metadata.GroupAttribute;
import fi.metatavu.keycloak.scim.server.patch.PatchOperation;
import fi.metatavu.keycloak.scim.server.consts.Schemas;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.GroupMembersInner;
import fi.metatavu.keycloak.scim.server.model.GroupsList;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import fi.metatavu.keycloak.scim.server.users.ScimManagedUsers;
import org.jboss.logging.Logger;
import org.keycloak.events.admin.OperationType;
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
import java.util.function.Predicate;
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

        GroupModel group = scimContext.getGroupScope().createGroup(scimGroup.getDisplayName());
        Predicate<UserModel> isManaged = getManagedUserPredicate(scimContext);

        if (scimGroup.getMembers() != null) {
            for (GroupMembersInner member : scimGroup.getMembers()) {
                // Users outside the SCIM-managed set are dropped like unknown ids
                UserModel user = session.users().getUserById(realm, member.getValue());
                if (user != null && isManaged.test(user)) {
                    user.joinGroup(group);
                }
            }
        }

        dispatchGroupCreateEvent(scimContext, group);

        return translateGroup(scimContext, group, AttributeSelection.DEFAULT, isManaged);
    }

    /**
     * Finds a group
     *
     * @param scimContext SCIM context
     * @param groupId group ID
     * @param attributeSelection attributes the response may carry
     * @return found group
     */
    public Group findGroup(
            ScimContext scimContext,
            String groupId,
            AttributeSelection attributeSelection
    ) {
        GroupModel group = scimContext.getGroupScope().findGroup(groupId);
        if (group == null) {
            return null;
        }

        return translateGroup(scimContext, group, attributeSelection, getManagedUserPredicate(scimContext));
    }

    /**
     * Lists groups
     *
     * @param scimContext SCIM context
     * @param scimFilter SCIM filter
     * @param firstResult zero-based offset of the first group on the page
     * @param maxResults page size
     * @param attributeSelection attributes the response may carry
     * @return groups list
     */
    public GroupsList listGroups(
            ScimContext scimContext,
            ScimFilter scimFilter,
            int firstResult,
            int maxResults,
            AttributeSelection attributeSelection
    ) {
        GroupScope groupScope = scimContext.getGroupScope();
        GroupsList result = new GroupsList();

        // For now only support to filter on display name
        List<GroupModel> filteredGroups;
        if(scimFilter instanceof ComparisonFilter(
                String attribute, ScimFilter.Operator operator, String value
        ) && operator == ScimFilter.Operator.EQ && attribute.equals(GroupAttribute.DISPLAY_NAME.getScimPath())){
            // Not paged here: the page is cut once below, and paging the query too applied the offset twice
            filteredGroups = groupScope.getGroupsByNameStream(value).toList();
        }else{
            filteredGroups = groupScope.getGroupsStream().toList();
        }

        // Resolved once for the whole page rather than once per group
        Predicate<UserModel> isManaged = getManagedUserPredicate(scimContext);
        List<Group> groups = filteredGroups.stream()
            .skip(firstResult)
            .limit(maxResults)
            .map(group -> translateGroup(scimContext, group, attributeSelection, isManaged))
            .collect(Collectors.toList());

        result.setTotalResults(filteredGroups.size());
        result.setStartIndex(ScimPagination.startIndex(firstResult));
        result.setItemsPerPage(groups.size());
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

        Predicate<UserModel> isManaged = getManagedUserPredicate(scimContext);

        // SCIM PUT is a full replace of the resource — reconcile members against the request.
        // Okta's Group Push uses PUT (not PATCH) with the desired final member list.
        // Only SCIM-managed users take part: members outside that set are invisible to the
        // client, so they are neither removed nor added here.
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

            // Remove managed members no longer in the desired set
            for (UserModel user : currentMembers) {
                if (!desiredIds.contains(user.getId()) && isManaged.test(user)) {
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
                if (user != null && isManaged.test(user)) {
                    user.joinGroup(existing);
                    if (userCache != null) {
                        userCache.evict(realm, user);
                    }
                    dispatchGroupMembershipJoinEvent(scimContext, existing, user);
                }
            }
        }

        return translateGroup(scimContext, existing, AttributeSelection.DEFAULT, isManaged);
    }

    /**
     * Patch group with SCIM group data
     *
     * <p>Operations are applied in order. When one of them fails, earlier
     * operations have already been applied to the session; the caller must
     * mark the Keycloak transaction rollback-only so the PATCH is applied as
     * a whole or not at all (RFC 7644 §3.5.2). Admin events are dispatched
     * only after every operation has succeeded.
     *
     * @param scimContext SCIM context
     * @param existing existing group
     * @param patchRequest patch request
     * @param attributeSelection attributes the response may carry
     * @return patched group, or null when the caller asked for no body
     */
    public fi.metatavu.keycloak.scim.server.model.Group patchGroup(
            ScimContext scimContext,
            GroupModel existing,
            fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest,
            AttributeSelection attributeSelection
    ) throws UnsupportedGroupPath, UnsupportedPatchOperation, InvalidGroupMemberReference, InvalidGroupPatchValue {
        Predicate<UserModel> isManaged = getManagedUserPredicate(scimContext);
        // Admin events are only dispatched once every operation has succeeded,
        // so a failed (and rolled back) PATCH emits none.
        List<Runnable> pendingEvents = new ArrayList<>();

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
                    applyGroupPatch(scimContext, op, attr, attrPath, entry.getValue(), existing, isManaged, pendingEvents);
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

            // Value can be null for REMOVE operations with path filters. ADD and
            // REPLACE need a value: fail the whole PATCH instead of skipping the
            // operation, so the client never gets a success for changes that
            // were not made (RFC 7644 §3.5.2).
            if (value == null && op != PatchOperation.REMOVE) {
                logger.warn("Value is null for patch operation: " + op);
                throw new InvalidGroupPatchValue("Missing value for '" + operation.getOp() + "' operation on path: " + path);
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

            applyGroupPatch(scimContext, op, groupAttribute, path, effectiveValue, existing, isManaged, pendingEvents);
        }

        pendingEvents.forEach(Runnable::run);

        // RFC 7644 §3.5.2: a successful PATCH may answer 204 No Content, and
        // MUST answer 200 with the resource when "attributes" was specified.
        // Only the latter costs a representation, so only the latter builds one.
        if (!attributeSelection.isExplicit()) {
            return null;
        }

        return translateGroup(scimContext, existing, attributeSelection, isManaged);
    }

    /**
     * Apply a single SCIM PatchOp on a Group.
     *
     * <p>Atomicity scope: per operation. If a PatchRequest contains multiple
     * operations, each is applied in order; when a later one fails, the caller
     * rolls back the Keycloak transaction so earlier operations are discarded
     * (see {@link #patchGroup}).
     *
     * <p>For MEMBERS modifications (ADD/REPLACE/REMOVE), every incoming member
     * ID is resolved via {@link #resolveMembers} before any mutation. The
     * first unresolved ID raises {@link InvalidGroupMemberReference}, so the
     * group's membership stays intact for that operation.
     *
     * <p>Only SCIM-managed users can be added or removed; a REPLACE leaves
     * members outside that set in place.
     *
     * <p>Used by both the path-less and path-based PatchOp branches of
     * {@link #patchGroup}.
     *
     * @param pendingEvents admin events to dispatch once the whole PATCH has succeeded
     */
    private void applyGroupPatch(
            ScimContext scimContext,
            PatchOperation op,
            GroupAttribute attr,
            String attrPath,
            Object value,
            GroupModel existing,
            Predicate<UserModel> isManaged,
            List<Runnable> pendingEvents
    ) throws InvalidGroupMemberReference, UnsupportedGroupPath {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        switch (op) {
            case REPLACE, ADD -> {
                switch (attr) {
                    case DISPLAY_NAME -> {
                        if (!(value instanceof String s)) {
                            throw new UnsupportedGroupPath("displayName requires a string value");
                        }
                        existing.setName(s);
                    }
                    case MEMBERS -> {
                        List<UserModel> resolved = resolveMembers(session, realm, value, isManaged);
                        if (op == PatchOperation.REPLACE) {
                            session.users().getGroupMembersStream(realm, existing)
                                    .filter(isManaged)
                                    .forEach(u -> {
                                        u.leaveGroup(existing);
                                        pendingEvents.add(() -> dispatchGroupMembershipLeaveEvent(scimContext, existing, u));
                                    });
                        }
                        for (UserModel u : resolved) {
                            u.joinGroup(existing);
                            pendingEvents.add(() -> dispatchGroupMembershipJoinEvent(scimContext, existing, u));
                        }
                    }
                }
            }
            case REMOVE -> {
                switch (attr) {
                    case DISPLAY_NAME -> existing.setName(null);
                    case MEMBERS -> {
                        // REMOVE shares the strict resolution path with REPLACE/ADD: an unknown
                        // member id surfaces as 400 InvalidGroupMemberReference rather than a
                        // silent no-op. SCIM clients with stale state get an actionable error
                        // instead of believing the membership change went through.
                        List<UserModel> resolved = resolveMembers(session, realm, value, isManaged);
                        for (UserModel u : resolved) {
                            u.leaveGroup(existing);
                            pendingEvents.add(() -> dispatchGroupMembershipLeaveEvent(scimContext, existing, u));
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
     * operation without partially applying changes. Atomicity across multiple
     * operations in the same PatchRequest comes from the transaction rollback
     * done by the caller of {@link #patchGroup}.
     *
     * <p>REMOVE uses this same path: an unknown member ID returns 400 rather
     * than silently no-oping, so SCIM clients with stale state receive an
     * actionable error instead of a false success.
     *
     * <p>A user outside the SCIM-managed set is reported exactly like an
     * unknown ID, so the client can neither change such a user's memberships
     * nor learn that the user exists.
     *
     * @param session Keycloak session
     * @param realm realm
     * @param value PatchOp value
     * @param isManaged predicate selecting SCIM-managed users
     * @return resolved users
     * @throws InvalidGroupMemberReference when an ID is unknown or not SCIM-managed
     */
    private List<UserModel> resolveMembers(
            KeycloakSession session,
            RealmModel realm,
            Object value,
            Predicate<UserModel> isManaged
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
            if (user == null || !isManaged.test(user)) {
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
     * Translates Keycloak group to SCIM group, honouring the client's attribute
     * selection.
     *
     * <p>The member list is only loaded when it is actually going to be
     * serialised. {@code members} is "returned": "default" in the core Group
     * schema (RFC 7643 §8.7.1), so a plain read still pays for the whole
     * membership — but a client that sends {@code excludedAttributes=members}
     * (as Microsoft Entra ID does on every group read) now costs a constant
     * amount of work instead of O(group size). Omitting the attribute rather
     * than nulling it is deliberate: RFC 7643 §2.5 treats null and the empty
     * array as equivalent to unassigned, so {@code "members": null} would
     * wrongly assert that the group is empty.
     *
     * <p>Only SCIM-managed members are listed, matching what GET /Users exposes.
     *
     * @param scimContext SCIM context
     * @param group group
     * @param attributeSelection attributes the response may carry
     * @param isManaged predicate selecting SCIM-managed users
     * @return SCIM group
     */
    private Group translateGroup(
            ScimContext scimContext,
            GroupModel group,
            AttributeSelection attributeSelection,
            Predicate<UserModel> isManaged
    ) {
        RealmModel realm = scimContext.getRealm();
        KeycloakSession session = scimContext.getSession();

        // id, schemas and meta are the minimum attribute set (RFC 7644 §3.9):
        // they are returned whatever the client selected.
        Group result = new Group()
                .id(group.getId())
                .schemas(Collections.singletonList(Schemas.GROUP_SCHEMA))
                .meta(getMeta(scimContext, "Group", String.format("Groups/%s", group.getId())));

        if (attributeSelection.includes(GroupAttribute.DISPLAY_NAME.getScimPath())) {
            result.setDisplayName(group.getName());
        }

        if (attributeSelection.includes(GroupAttribute.MEMBERS.getScimPath())) {
            result.setMembers(session.users().getGroupMembersStream(realm, group)
                    .filter(isManaged)
                    .map(member -> new GroupMembersInner()
                            .value(member.getId())
                            .display(member.getUsername())
                    )
                    .toList());
        }

        return result;
    }

    /**
     * Returns the predicate selecting users that group operations may see and modify.
     *
     * Same rule as {@link ScimManagedUsers#predicate}, but fails closed like
     * {@link ScimManagedUsers#isManaged}: when the scim-managed role is missing and the
     * manage-all-users setting is off, no user is treated as managed. The group scope
     * narrows it further, e.g. to the members of an organization.
     *
     * @param scimContext SCIM context
     * @return predicate that is true for SCIM-managed users
     */
    private Predicate<UserModel> getManagedUserPredicate(ScimContext scimContext) {
        if (!scimContext.getConfig().getManageAllUsers()
                && scimContext.getRealm().getRole(ScimRoles.SCIM_MANAGED_ROLE) == null) {
            return user -> false;
        }

        return ScimManagedUsers.predicate(scimContext).and(scimContext.getGroupScope()::canBeMember);
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
                scimContext.getGroupScope().getGroupResourceType(),
                scimContext.getGroupScope().getGroupResourcePath(group),
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
                scimContext.getGroupScope().getGroupResourceType(),
                scimContext.getGroupScope().getGroupResourcePath(group),
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
                scimContext.getGroupScope().getGroupMembershipResourceType(),
                scimContext.getGroupScope().getGroupResourcePath(group) + "/members/" + user.getId(),
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
                scimContext.getGroupScope().getGroupMembershipResourceType(),
                scimContext.getGroupScope().getGroupResourcePath(group) + "/members/" + user.getId(),
                groupRepresentation,
                Map.of(
                        UserModel.USERNAME, user.getUsername(),
                        UserModel.EMAIL, user.getEmail() == null ? "" : user.getEmail()
                )
        );
    }
}

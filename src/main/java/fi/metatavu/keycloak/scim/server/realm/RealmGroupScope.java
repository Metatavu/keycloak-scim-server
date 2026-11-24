package fi.metatavu.keycloak.scim.server.realm;

import fi.metatavu.keycloak.scim.server.groups.GroupScope;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.stream.Stream;

/**
 * Realm groups. Organization groups live in the same table and are excluded, so the realm
 * endpoint can neither read nor change them.
 */
class RealmGroupScope implements GroupScope {

    private final KeycloakSession session;
    private final RealmModel realm;

    RealmGroupScope(KeycloakSession session, RealmModel realm) {
        this.session = session;
        this.realm = realm;
    }

    @Override
    public GroupModel createGroup(String displayName) {
        return session.groups().createGroup(realm, displayName);
    }

    @Override
    public GroupModel findGroup(String groupId) {
        GroupModel group = session.groups().getGroupById(realm, groupId);
        return group != null && isRealmGroup(group) ? group : null;
    }

    @Override
    public Stream<GroupModel> getGroupsStream() {
        return session.groups().getGroupsStream(realm).filter(RealmGroupScope::isRealmGroup);
    }

    @Override
    public Stream<GroupModel> getGroupsByNameStream(String displayName) {
        return session.groups().searchForGroupByNameStream(realm, displayName, true, null, null)
            .filter(RealmGroupScope::isRealmGroup);
    }

    @Override
    public boolean canBeMember(UserModel user) {
        return true;
    }

    @Override
    public ResourceType getGroupResourceType() {
        return ResourceType.GROUP;
    }

    @Override
    public ResourceType getGroupMembershipResourceType() {
        return ResourceType.GROUP_MEMBERSHIP;
    }

    @Override
    public String getGroupResourcePath(GroupModel group) {
        return "groups/" + group.getId();
    }

    private static boolean isRealmGroup(GroupModel group) {
        return group.getType() == GroupModel.Type.REALM;
    }

}

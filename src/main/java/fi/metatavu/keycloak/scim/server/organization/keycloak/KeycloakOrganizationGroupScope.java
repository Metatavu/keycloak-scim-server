package fi.metatavu.keycloak.scim.server.organization.keycloak;

import fi.metatavu.keycloak.scim.server.groups.GroupScope;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.organization.OrganizationProvider;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * The groups of one Keycloak organization. SCIM creates them flat, as top-level organization groups.
 *
 * <p>Keycloak keeps every organization member in an internal group that is itself an organization
 * group; it is excluded, so SCIM can neither rename nor delete it nor manage membership through it.
 */
class KeycloakOrganizationGroupScope implements GroupScope {

    private final KeycloakSession session;
    private final RealmModel realm;
    private final OrganizationModel organization;

    KeycloakOrganizationGroupScope(KeycloakSession session, RealmModel realm, OrganizationModel organization) {
        this.session = session;
        this.realm = realm;
        this.organization = organization;
    }

    @Override
    public GroupModel createGroup(String displayName) {
        return getOrganizationProvider().createGroup(organization, displayName, null);
    }

    @Override
    public GroupModel findGroup(String groupId) {
        GroupModel group = session.groups().getGroupById(realm, groupId);
        return group != null && belongsToOrganization(group) ? group : null;
    }

    @Override
    public Stream<GroupModel> getGroupsStream() {
        return getOrganizationProvider().searchGroupsByName(organization, "", false, null, null);
    }

    @Override
    public Stream<GroupModel> getGroupsByNameStream(String displayName) {
        return getOrganizationProvider().searchGroupsByName(organization, displayName, true, null, null);
    }

    @Override
    public boolean canBeMember(UserModel user) {
        return getOrganizationProvider().isMember(organization, user);
    }

    @Override
    public ResourceType getGroupResourceType() {
        return ResourceType.ORGANIZATION_GROUP;
    }

    @Override
    public ResourceType getGroupMembershipResourceType() {
        return ResourceType.ORGANIZATION_GROUP_MEMBERSHIP;
    }

    @Override
    public String getGroupResourcePath(GroupModel group) {
        return "organizations/" + organization.getId() + "/groups/" + group.getId();
    }

    private boolean belongsToOrganization(GroupModel group) {
        return group.getType() == GroupModel.Type.ORGANIZATION
            && group.getOrganization() != null
            && Objects.equals(group.getOrganization().getId(), organization.getId())
            && !Objects.equals(group.getId(), getOrganizationProvider().getOrganizationGroup(organization).getId());
    }

    private OrganizationProvider getOrganizationProvider() {
        return session.getProvider(OrganizationProvider.class);
    }

}

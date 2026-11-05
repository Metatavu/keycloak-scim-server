package fi.metatavu.keycloak.scim.server.test.tests.unit;

import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import fi.metatavu.keycloak.scim.server.consts.ScimRoles;
import fi.metatavu.keycloak.scim.server.users.ScimManagedUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SCIM user visibility and delete decision
 */
@ExtendWith(MockitoExtension.class)
class ScimManagedUsersTest {

    @Mock
    private ScimContext scimContext;

    @Mock
    private ScimConfig config;

    @Mock
    private RealmModel realm;

    @Mock
    private RoleModel scimManagedRole;

    @Mock
    private UserModel managedUser;

    @Mock
    private UserModel unmanagedUser;

    @Mock
    private UserModel serviceAccount;

    @BeforeEach
    void setUp() {
        lenient().when(scimContext.getConfig()).thenReturn(config);
        lenient().when(scimContext.getRealm()).thenReturn(realm);
        lenient().when(realm.getRole(ScimRoles.SCIM_MANAGED_ROLE)).thenReturn(scimManagedRole);
        lenient().when(managedUser.hasRole(scimManagedRole)).thenReturn(true);
        lenient().when(unmanagedUser.hasRole(scimManagedRole)).thenReturn(false);
        lenient().when(serviceAccount.hasRole(scimManagedRole)).thenReturn(true);
        lenient().when(serviceAccount.getServiceAccountClientLink()).thenReturn("some-client-id");
    }

    @Test
    void listIncludesOnlyRoleHoldersByDefault() {
        when(config.getManageAllUsers()).thenReturn(false);

        assertTrue(ScimManagedUsers.predicate(scimContext).test(managedUser));
        assertFalse(ScimManagedUsers.predicate(scimContext).test(unmanagedUser));
    }

    @Test
    void listIncludesEveryoneWhenManageAllUsersEnabled() {
        when(config.getManageAllUsers()).thenReturn(true);

        assertTrue(ScimManagedUsers.predicate(scimContext).test(managedUser));
        assertTrue(ScimManagedUsers.predicate(scimContext).test(unmanagedUser));
    }

    @Test
    void listNeverIncludesServiceAccounts() {
        when(config.getManageAllUsers()).thenReturn(true);
        assertFalse(ScimManagedUsers.predicate(scimContext).test(serviceAccount));

        when(config.getManageAllUsers()).thenReturn(false);
        assertFalse(ScimManagedUsers.predicate(scimContext).test(serviceAccount));
    }

    @Test
    void listFailsWhenRoleIsMissingAndSettingIsOff() {
        when(config.getManageAllUsers()).thenReturn(false);
        when(realm.getRole(ScimRoles.SCIM_MANAGED_ROLE)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> ScimManagedUsers.predicate(scimContext));
    }

    @Test
    void deleteAllowsOnlyRoleHoldersByDefault() {
        when(config.getManageAllUsers()).thenReturn(false);

        assertTrue(ScimManagedUsers.isManaged(scimContext, managedUser));
        assertFalse(ScimManagedUsers.isManaged(scimContext, unmanagedUser));
    }

    @Test
    void deleteAllowsEveryoneWhenManageAllUsersEnabled() {
        when(config.getManageAllUsers()).thenReturn(true);

        assertTrue(ScimManagedUsers.isManaged(scimContext, managedUser));
        assertTrue(ScimManagedUsers.isManaged(scimContext, unmanagedUser));
    }

    @Test
    void deleteNeverAllowsServiceAccounts() {
        // service accounts are rejected before the setting is consulted, hence the lenient stubs
        lenient().when(config.getManageAllUsers()).thenReturn(true);
        assertFalse(ScimManagedUsers.isManaged(scimContext, serviceAccount));

        lenient().when(config.getManageAllUsers()).thenReturn(false);
        assertFalse(ScimManagedUsers.isManaged(scimContext, serviceAccount));
    }

    @Test
    void deleteFailsClosedWhenRoleIsMissingAndSettingIsOff() {
        when(config.getManageAllUsers()).thenReturn(false);
        when(realm.getRole(ScimRoles.SCIM_MANAGED_ROLE)).thenReturn(null);

        assertFalse(ScimManagedUsers.isManaged(scimContext, managedUser));
        assertFalse(ScimManagedUsers.isManaged(scimContext, unmanagedUser));
    }

}

package fi.metatavu.keycloak.scim.server.test.tests.unit;

import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.metadata.StringUserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import fi.metatavu.keycloak.scim.server.realm.RealmScimConfig;
import fi.metatavu.keycloak.scim.server.users.UserProfileValidationException;
import fi.metatavu.keycloak.scim.server.users.UsersController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.userprofile.UserProfileProvider;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsersControllerTest {

    @Mock
    private ScimContext scimContext;
    @Mock
    private UserModel userModel;
    @Mock
    private RealmModel realmModel;
    @Mock
    private UserAttributes userAttributes;
    @Mock
    private StringUserAttribute displayNameAttribute;
    @Mock
    private KeycloakSession keycloakSession;
    @Mock
    private SubjectCredentialManager credentialManager;

    private UsersController usersController;

    /**
     * Test-specific subclass that overrides protected methods to avoid Profile.getInstance() issues
     */
    private static class TestableUsersController extends UsersController {
        @Override
        protected void dispatchUserUpdateEvent(ScimContext scimContext, UserModel user) {
            // Do nothing to avoid Profile.getInstance() issues in tests
        }
    }

    @BeforeEach
    void setUp() {
        usersController = new TestableUsersController();
    }

    @Test
    void testPatchUserWithDisplayNameValue() throws UnsupportedPatchOperation, UserProfileValidationException {
        // Given: A patch request with displayName operation containing a value
        fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest =
                new fi.metatavu.keycloak.scim.server.model.PatchRequest();

        PatchRequestOperationsInner operation = new PatchRequestOperationsInner();
        operation.setOp("replace");
        operation.setPath("displayName");
        operation.setValue("John Doe");

        patchRequest.setOperations(List.of(operation));

        // Mock ScimContext and its dependencies
        RealmScimConfig realmScimConfig = new RealmScimConfig(realmModel);
        when(scimContext.getConfig()).thenReturn(realmScimConfig);
        when(scimContext.getSession()).thenReturn(keycloakSession);
        lenient().when(scimContext.getRealm()).thenReturn(realmModel);
        when(scimContext.getServerBaseUri()).thenReturn(URI.create("http://localhost:8080/auth/realms/master/scim"));

        // Mock UserProfileProvider to return null (validation skipped in this case)
        when(keycloakSession.getProvider(UserProfileProvider.class)).thenReturn(null);

        // Mock userModel basic properties needed for translateUser
        when(userModel.getId()).thenReturn("test-user-id");
        lenient().when(userModel.getUsername()).thenReturn("testuser");
        when(userModel.getEmail()).thenReturn("test@example.com");
        when(userModel.isEnabled()).thenReturn(true);

        // Mock UserAttributes to return displayName as a mapped attribute
        doReturn(displayNameAttribute).when(userAttributes).findByScimPath("displayName");
        when(displayNameAttribute.getSourceId()).thenReturn("displayName");
        when(userAttributes.listBySource(any())).thenReturn(List.of());

        // Execute
        fi.metatavu.keycloak.scim.server.model.User result = usersController.patchUser(
                scimContext,
                userAttributes,
                userModel,
                patchRequest
        );

        // Then: The displayName should be written through the mapped attribute
        verify(displayNameAttribute).write(userModel, "John Doe");
        assertNotNull(result);
    }
}

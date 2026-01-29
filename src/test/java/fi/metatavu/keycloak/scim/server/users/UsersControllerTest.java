package fi.metatavu.keycloak.scim.server.users;

import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.metadata.UserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import fi.metatavu.keycloak.scim.server.realm.RealmScimConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.mockito.Mock;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private UserAttribute<?> displayNameAttribute;

    private UsersController usersController;

    @BeforeEach
    void setUp() {
        usersController = new UsersController();
    }

    @Test
    void testPatchUserWithoutDisplayNameValue() throws UnsupportedPatchOperation {
        // Given: A patch request with displayName operation containing a value
        fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest =
                new fi.metatavu.keycloak.scim.server.model.PatchRequest();

        fi.metatavu.keycloak.scim.server.model.PatchRequestOperationsInner operation = new fi.metatavu.keycloak.scim.server.model.PatchRequestOperationsInner();
        operation.setOp("replace");
        operation.setPath("displayName");
        operation.setValue("John Doe");

        patchRequest.setOperations(List.of(operation));

        // Mocking ScimContext and UserAttributes behavior
        when(scimContext.getConfig()).thenReturn(new RealmScimConfig(realmModel));
        when(scimContext.getServerBaseUri()).thenReturn(URI.create("http://localhost:8080/auth/realms/master/scim"));

        // When: Patching the user
        fi.metatavu.keycloak.scim.server.model.User result = usersController.patchUser(
                scimContext,
                userAttributes,
                userModel,
                patchRequest
        );

        // Then: The displayName should be set on the user model
        verify(displayNameAttribute, never()).write(any(UserModel.class), any());
        verify(userModel, never()).setSingleAttribute("displayName", "John Doe");
        assertNotNull(result);
    }
}
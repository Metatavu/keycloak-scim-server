package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for organization SCIM configuration via ComponentModel (User Federation GUI)
 */
@Testcontainers
public class OrganizationComponentConfigTestsIT extends AbstractOrganizationScimTest {

    private String createdComponentId = null;

    @AfterEach
    void cleanupComponent() {
        if (createdComponentId != null) {
            try {
                getKeycloakContainer().getKeycloakAdminClient()
                    .realm(TestConsts.ORGANIZATIONS_REALM)
                    .components()
                    .component(createdComponentId)
                    .remove();
            } catch (Exception e) {
                // ignore cleanup errors
            }
            createdComponentId = null;
        }
    }

    @Test
    void testComponentConfiguredOrganizationCreateUser() throws ApiException {
        createComponentConfig(
            TestConsts.ORGANIZATION_6_COMPONENT_CONFIG_ID,
            "EXTERNAL",
            "*",
            "account",
            "http://localhost:8080/realms/external/protocol/openid-connect/certs",
            null,
            true
        );

        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_6_COMPONENT_CONFIG_ID);

        User user = new User();
        user.setUserName("component-config-user");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("Component", "User"));
        user.setEmails(getEmails("component.user@example.com"));

        User created = scimClient.createUser(user);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("component-config-user", created.getUserName());

        // Clean up user
        deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, created.getId());
    }

    @Test
    void testComponentConfiguredOrganizationListUsers() throws ApiException {
        createComponentConfig(
            TestConsts.ORGANIZATION_6_COMPONENT_CONFIG_ID,
            "EXTERNAL",
            "*",
            "account",
            "http://localhost:8080/realms/external/protocol/openid-connect/certs",
            null,
            true
        );

        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_6_COMPONENT_CONFIG_ID);

        // List users on empty org should return empty list
        var usersList = scimClient.listUsers(null, null, null);
        assertNotNull(usersList);
        assertEquals(0, usersList.getTotalResults());
    }

    @Test
    void testDisabledComponentFallsBackToOrgAttributes() throws ApiException {
        // org1 has SCIM attributes configured. Create a disabled ComponentModel for it.
        createComponentConfig(
            TestConsts.ORGANIZATION_1_ID,
            "EXTERNAL",
            "wrong-issuer",
            "wrong-audience",
            "http://wrong-jwks-uri",
            null,
            false // disabled
        );

        // Should still work because disabled component falls back to org attributes
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        var usersList = scimClient.listUsers(null, null, null);
        assertNotNull(usersList);
    }

    @Test
    void testComponentConfigWithSharedSecret() throws ApiException {
        // Configure org6 with shared secret auth
        createComponentConfig(
            TestConsts.ORGANIZATION_6_COMPONENT_CONFIG_ID,
            "EXTERNAL",
            null,
            null,
            null,
            "$argon2id$v=19$m=16,t=2,p=1$UUppcFAwQUp0SkQwVGZudQ$j5RwfEzt3Gvwpbqp0VDcJg", // hash of "tutu"
            true
        );

        // Use shared secret "tutu" to authenticate
        ScimClient scimClient = getAuthenticatedSharedSecretScimClient(
            TestConsts.ORGANIZATION_6_COMPONENT_CONFIG_ID,
            "tutu"
        );

        var usersList = scimClient.listUsers(null, null, null);
        assertNotNull(usersList);
        assertEquals(0, usersList.getTotalResults());
    }

    @Test
    void testNoConfigReturnsError() {
        // org6 has no SCIM attributes and no ComponentModel -> should fail
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_6_COMPONENT_CONFIG_ID);

        assertThrows(ApiException.class, () -> scimClient.listUsers(null, null, null));
    }

    /**
     * Creates a ComponentModel for organization SCIM configuration
     */
    private void createComponentConfig(
        String organizationId,
        String authMode,
        String issuer,
        String audience,
        String jwksUri,
        String sharedSecret,
        boolean enabled
    ) {
        Keycloak adminClient = getKeycloakContainer().getKeycloakAdminClient();

        ComponentRepresentation component = new ComponentRepresentation();
        component.setId(organizationId);
        component.setName("Organization SCIM");
        component.setProviderId("Organization SCIM");
        component.setProviderType("org.keycloak.storage.UserStorageProvider");
        component.setParentId(TestConsts.ORGANIZATIONS_REALM_ID);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put("ORGANIZATION_ID", List.of(organizationId));
        config.put("SCIM_AUTHENTICATION_MODE", List.of(authMode));
        config.put("ENABLED", List.of(String.valueOf(enabled)));

        if (issuer != null) {
            config.put("SCIM_EXTERNAL_ISSUER", List.of(issuer));
        }
        if (audience != null) {
            config.put("SCIM_EXTERNAL_AUDIENCE", List.of(audience));
        }
        if (jwksUri != null) {
            config.put("SCIM_EXTERNAL_JWKS_URI", List.of(jwksUri));
        }
        if (sharedSecret != null) {
            config.put("SCIM_EXTERNAL_SHARED_SECRET", List.of(sharedSecret));
        }

        component.setConfig(config);

        try (Response response = adminClient
            .realm(TestConsts.ORGANIZATIONS_REALM)
            .components()
            .add(component)) {
            assertEquals(201, response.getStatus(), "Failed to create component: " + response.getStatusInfo());
        }

        createdComponentId = organizationId;
    }
}

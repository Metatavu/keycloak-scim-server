package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.ResourceType;
import fi.metatavu.keycloak.scim.server.test.client.model.ResourceTypeListResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for realm server SCIM 2.0 user find (GET /Users/{id}) endpoint
 */
@Testcontainers
public class RealmResourceTypesTestsIT extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
        .withNetwork(network)
        .withNetworkAliases("scim-keycloak")
        .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
        .withRealmImportFile("kc-test.json")
        .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @Test
    void testResourceTypes() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        ResourceTypeListResponse listResponse = scimClient.getResourceTypes();
        List<ResourceType> resourceTypes = listResponse.getResources();
        assertNotNull(resourceTypes);
        assertEquals(2, resourceTypes.size());
        assertResourceType(resourceTypes.get(0), "User", "/Users", "User Account");
        assertResourceType(resourceTypes.get(1), "Group", "/Groups", "Group");
    }

    /**
     * Asserts resource type
     *
     * @param resourceType resource type
     * @param id id
     * @param path path
     * @param description description
     */
    private void assertResourceType(
        ResourceType resourceType,
        String id,
        String path,
        String description
    ) {
        assertArrayEquals(new String[] { "urn:ietf:params:scim:schemas:core:2.0:ResourceType" }, resourceType.getSchemas().toArray());
        assertEquals(id, resourceType.getId());
        assertEquals(id, resourceType.getName());
        assertEquals(path, resourceType.getEndpoint());
        assertEquals(description, resourceType.getDescription());
        assertEquals("urn:ietf:params:scim:schemas:core:2.0:" + id, resourceType.getSchema());
        assertNotNull(resourceType.getSchemaExtensions());
        assertEquals(0, resourceType.getSchemaExtensions().size());
        assertNotNull(resourceType.getMeta());
        assertEquals(id, resourceType.getMeta().getResourceType());
        assertEquals(getScimUri().resolve(String.format("ResourceTypes/%s", id)), resourceType.getMeta().getLocation());
    }

}
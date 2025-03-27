package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.ResourceType;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 user find (GET /Users/{id}) endpoint
 */
@Testcontainers
public class ResourceTypesTestsIT extends AbstractScimTest {

    @Test
    void testResourceTypes() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        List<ResourceType> resourceTypes = scimClient.listResourceTypes();
        assertNotNull(resourceTypes);
        assertEquals(1, resourceTypes.size());
        assertArrayEquals(new String[] { "urn:ietf:params:scim:schemas:core:2.0:ResourceType" }, resourceTypes.getFirst().getSchemas().toArray());
        assertEquals("User", resourceTypes.getFirst().getId());
        assertEquals("User", resourceTypes.getFirst().getName());
        assertEquals("/Users", resourceTypes.getFirst().getEndpoint());
        assertEquals("User Account", resourceTypes.getFirst().getDescription());
        assertEquals("urn:ietf:params:scim:schemas:core:2.0:User", resourceTypes.getFirst().getSchema());
        assertNotNull(resourceTypes.getFirst().getSchemaExtensions());
        assertEquals(0, resourceTypes.getFirst().getSchemaExtensions().size());
        assertNotNull(resourceTypes.getFirst().getMeta());
        assertEquals("ResourceType", resourceTypes.getFirst().getMeta().getResourceType());
        assertEquals(getScimUri().resolve("/realms/test/scim/v2/ResourceTypes/User"), resourceTypes.getFirst().getMeta().getLocation());
    }

}
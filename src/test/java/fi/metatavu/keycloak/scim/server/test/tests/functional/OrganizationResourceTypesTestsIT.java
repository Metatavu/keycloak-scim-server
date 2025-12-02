package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 resource types endpoint
 */
@Testcontainers
public class OrganizationResourceTypesTestsIT extends AbstractOrganizationScimTest {

    @Test
    void testResourceTypesOrg1() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        ResourceTypeListResponse listResponse = scimClient.getResourceTypes();
        List<ResourceType> resourceTypes = listResponse.getResources();
        assertNotNull(resourceTypes);
        assertEquals(2, resourceTypes.size());
        assertResourceType(resourceTypes.get(0), "User", "/Users", "User Account");
        assertResourceType(resourceTypes.get(1), "Group", "/Groups", "Group");
    }

    @Test
    void testUserResourceTypeOrg1() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        ResourceType resourceType = scimClient.findResourceType("User");
        assertNotNull(resourceType);
        assertResourceType(resourceType, "User", "/Users", "User Account");
    }

    @Test
    void testGroupResourceTypeOrg1() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        ResourceType resourceType = scimClient.findResourceType("Group");
        assertNotNull(resourceType);
        assertResourceType(resourceType, "Group", "/Groups", "Group");
    }

    @Test
    void testResourceTypesOrg3() throws ApiException {
        // argon2i
        ScimClient scimClient = getAuthenticatedSharedSecretScimClient(TestConsts.ORGANIZATION_3_ID, "toto");
        scimClient.getResourceTypes();
    }

    @Test
    void testResourceTypesOrg4() throws ApiException {
        // argon2d
        ScimClient scimClient = getAuthenticatedSharedSecretScimClient(TestConsts.ORGANIZATION_4_ID, "titi");
        scimClient.getResourceTypes();
    }

    @Test
    void testResourceTypesOrg5() throws ApiException {
        // argon2d
        ScimClient scimClient = getAuthenticatedSharedSecretScimClient(TestConsts.ORGANIZATION_5_ID, "password");
        scimClient.getResourceTypes();
    }

    @Test
    void testResourceTypesOrg3WrongToken() {
        assertErrorAuthOrganization("wrongtoken");
    }

    @Test
    void testResourceTypesOrg3EmptyToken() {
        assertErrorAuthOrganization("");
    }

    @Test
    void testResourceTypesOrg3NullToken() {
        assertErrorAuthOrganization(null);
    }

    @Test
    void testResourceTypesOrg3UsingOrg4SharedSecret() {
        assertErrorAuthOrganization("titi");
    }

    private void assertErrorAuthOrganization(String wrongToken) {
        assertThrows(
            ApiException.class, () -> {
                ScimClient scimClient = getAuthenticatedSharedSecretScimClient(TestConsts.ORGANIZATION_3_ID, wrongToken);
                scimClient.getResourceTypes();
            }
        );
    }

    /**
     * Asserts a resource type
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
        assertEquals(getScimUri(TestConsts.ORGANIZATION_1_ID).resolve(String.format("ResourceTypes/%s", id)), resourceType.getMeta().getLocation());
    }

}

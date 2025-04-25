package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.SchemaAttribute;
import fi.metatavu.keycloak.scim.server.test.client.model.SchemaListItem;
import fi.metatavu.keycloak.scim.server.test.client.model.SchemaListResponse;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 Schemas endpoint
 */
@Testcontainers
public class RealmSchemasTestsIT extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
        .withNetwork(network)
        .withEnv("SCIM_AUTHENTICATION_MODE", "KEYCLOAK")
        .withNetworkAliases("scim-keycloak")
        .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
        .withRealmImportFile("kc-test.json")
        .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @Test
    void testSchemas() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        SchemaListResponse listResponse = scimClient.getSchemas();
        assertNotNull(listResponse);

        assertNotNull(listResponse.getSchemas());
        assertTrue(listResponse.getSchemas().contains("urn:ietf:params:scim:api:messages:2.0:ListResponse"));

        List<SchemaListItem> schemas = listResponse.getResources();
        assertNotNull(schemas);
        assertEquals(2, schemas.size());

        assertUserSchema(schemas.stream().filter(s -> s.getId().equals("urn:ietf:params:scim:schemas:core:2.0:User")).findFirst().orElseThrow());
        assertGroupSchema(schemas.stream().filter(s -> s.getId().equals("urn:ietf:params:scim:schemas:core:2.0:Group")).findFirst().orElseThrow());
    }

    @Test
    void testUserSchema() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        SchemaListItem schema = scimClient.findSchema("urn:ietf:params:scim:schemas:core:2.0:User");
        assertNotNull(schema);
        assertUserSchema(schema);
    }

    @Test
    void testGroupSchema() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();
        SchemaListItem schema = scimClient.findSchema("urn:ietf:params:scim:schemas:core:2.0:Group");
        assertNotNull(schema);
        assertGroupSchema(schema);
    }

    /**
     * Asserts that the user schema is correct
     *
     * @param schema schema
     */
    private void assertUserSchema(SchemaListItem schema) {
        assertEquals("urn:ietf:params:scim:schemas:core:2.0:User", schema.getId());
        assertEquals("User", schema.getName());
        assertNotNull(schema.getDescription());
        assertNotNull(schema.getAttributes());
        assertEquals(8, schema.getAttributes().size());

        assertUserAttribute(schema.getAttributes(), "userName", SchemaAttribute.TypeEnum.STRING);
        assertUserAttribute(schema.getAttributes(), "email", SchemaAttribute.TypeEnum.STRING);
        assertUserAttribute(schema.getAttributes(), "name.givenName", SchemaAttribute.TypeEnum.STRING);
        assertUserAttribute(schema.getAttributes(), "name.familyName", SchemaAttribute.TypeEnum.STRING);
        assertUserAttribute(schema.getAttributes(), "active", SchemaAttribute.TypeEnum.BOOLEAN);
        assertUserAttribute(schema.getAttributes(), "displayName", SchemaAttribute.TypeEnum.STRING);
        assertUserAttribute(schema.getAttributes(), "externalId", SchemaAttribute.TypeEnum.STRING);
        assertUserAttribute(schema.getAttributes(), "preferredLanguage", SchemaAttribute.TypeEnum.STRING);
    }

    /**
     * Asserts that the group schema is correct
     *
     * @param schema schema
     */
    private void assertGroupSchema(SchemaListItem schema) {
        assertEquals("urn:ietf:params:scim:schemas:core:2.0:Group", schema.getId());
        assertEquals("Group", schema.getName());
        assertNotNull(schema.getDescription());
        assertNotNull(schema.getAttributes());
        assertEquals(2, schema.getAttributes().size());
        assertGroupAttribute(schema.getAttributes(), "displayName", SchemaAttribute.TypeEnum.STRING);
        assertGroupAttribute(schema.getAttributes(), "members", SchemaAttribute.TypeEnum.COMPLEX);
    }

    /**
     * Asserts that the user schema contains an attribute with the given name and type
     *
     * @param attributes schema attributes
     * @param name attribute name
     * @param type attribute type
     */
    private void assertUserAttribute(List<SchemaAttribute> attributes, String name, SchemaAttribute.TypeEnum type) {
        Optional<SchemaAttribute> attributeOptional = attributes.stream().filter(a -> a.getName().equals(name)).findFirst();
        assertTrue(attributeOptional.isPresent());

        SchemaAttribute attribute = attributeOptional.get();

        assertEquals(name, attribute.getName());
        assertEquals(type, attribute.getType());
    }

    /**
     * Asserts that the group schema contains an attribute with the given name and type
     *
     * @param attributes schema attributes
     * @param name attribute name
     * @param type attribute type
     */
    private void assertGroupAttribute(List<SchemaAttribute> attributes, String name, SchemaAttribute.TypeEnum type) {
        Optional<SchemaAttribute> attributeOptional = attributes.stream().filter(a -> a.getName().equals(name)).findFirst();
        assertTrue(attributeOptional.isPresent());

        SchemaAttribute attribute = attributeOptional.get();

        assertEquals(name, attribute.getName());
        assertEquals(type, attribute.getType());
    }

}
package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.ServiceProviderConfig;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for organization-scoped ServiceProviderConfig endpoint
 */
@Testcontainers
public class OrganizationsServiceProviderConfigTestsIT extends AbstractOrganizationScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
            .withNetwork(network)
            .withNetworkAliases("scim-keycloak")
            .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
            .withRealmImportFiles("kc-organizations.json", "kc-external.json")
            .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @Test
    void testOrganizationServiceProviderConfigOrg1() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
        ServiceProviderConfig serviceProviderConfig = scimClient.getServiceProviderConfig();
        assertServiceProviderConfig(serviceProviderConfig, TestConsts.ORGANIZATION_1_ID);
    }

    @Test
    void testOrganizationServiceProviderConfigOrg2() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_2_ID);
        ServiceProviderConfig serviceProviderConfig = scimClient.getServiceProviderConfig();
        assertServiceProviderConfig(serviceProviderConfig, TestConsts.ORGANIZATION_2_ID);
    }

    private void assertServiceProviderConfig(ServiceProviderConfig config, String organizationId) {
        assertArrayEquals(new String[]{"urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"}, config.getSchemas().toArray());
        assertTrue(config.getPatch().getSupported());
        assertFalse(config.getBulk().getSupported());
        assertEquals(1000, config.getBulk().getMaxOperations());
        assertEquals(1048576, config.getBulk().getMaxPayloadSize());
        assertTrue(config.getFilter().getSupported());
        assertEquals(200, config.getFilter().getMaxResults());
        assertFalse(config.getChangePassword().getSupported());
        assertFalse(config.getSort().getSupported());
        assertFalse(config.getEtag().getSupported());
        assertNotNull(config.getAuthenticationSchemes());
        assertEquals(1, config.getAuthenticationSchemes().size());
        assertEquals("OAuth Bearer Token", config.getAuthenticationSchemes().get(0).getName());
        assertEquals("ServiceProviderConfig", config.getMeta().getResourceType());
        assertEquals(getScimUri(organizationId).resolve(String.format("/realms/organizations/scim/v2/organizations/%s/ServiceProviderConfig", organizationId)), config.getMeta().getLocation());
    }
}
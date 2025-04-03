package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.AuthenticationScheme;
import fi.metatavu.keycloak.scim.server.test.client.model.ServiceProviderConfig;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for realm server SCIM 2.0 ServiceProviderConfig endpoint
 */
@Testcontainers
public class RealmServiceProviderConfigTestsIT extends AbstractScimTest {

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
    void testServiceProviderConfig() throws ApiException {
        ScimClient scimClient = getAuthenticatedScimClient();

        ServiceProviderConfig config = scimClient.getServiceProviderConfig();
        assertNotNull(config);

        assertEquals(List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"), config.getSchemas());
        assertEquals(URI.create("http://example.com/help/scim.html"), config.getDocumentationUri());

        assertNotNull(config.getPatch());
        assertTrue(config.getPatch().getSupported());

        assertNotNull(config.getBulk());
        assertFalse(config.getBulk().getSupported());
        assertEquals(1000, config.getBulk().getMaxOperations());
        assertEquals(1048576, config.getBulk().getMaxPayloadSize());

        assertNotNull(config.getFilter());
        assertTrue(config.getFilter().getSupported());
        assertEquals(200, config.getFilter().getMaxResults());

        assertNotNull(config.getChangePassword());
        assertFalse(config.getChangePassword().getSupported());

        assertNotNull(config.getSort());
        assertFalse(config.getSort().getSupported());

        assertNotNull(config.getEtag());
        assertFalse(config.getEtag().getSupported());

        assertNotNull(config.getAuthenticationSchemes());
        assertEquals(1, config.getAuthenticationSchemes().size());

        AuthenticationScheme scheme = config.getAuthenticationSchemes().get(0);
        assertEquals("OAuth Bearer Token", scheme.getName());
        assertEquals("Authentication scheme using the OAuth Bearer Token Standard", scheme.getDescription());
        assertEquals(URI.create("http://www.rfc-editor.org/info/rfc6750"), scheme.getSpecUri());
        assertEquals(URI.create("http://example.com/help/oauth.html"), scheme.getDocumentationUri());
        assertEquals("oauthbearertoken", scheme.getType());
        assertTrue(scheme.getPrimary());

        assertNotNull(config.getMeta());
        assertEquals("ServiceProviderConfig", config.getMeta().getResourceType());

        URI expectedLocation = getScimUri().resolve("/realms/test/scim/v2/ServiceProviderConfig");
        assertEquals(expectedLocation, config.getMeta().getLocation());
    }

}
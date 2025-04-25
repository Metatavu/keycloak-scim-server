package fi.metatavu.keycloak.scim.server.test.tests.ui;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationSeleniumScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for SCIM 2.0 user linking with external identity provider
 */
@Testcontainers
public class OrganizationLinkIdpTestsIT extends AbstractOrganizationSeleniumScimTest {

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

    /**
     * Test that SCIM synchronized user can log in with external identity provider
     */
    @Test
    void testLinkIdp() throws ApiException {
        RemoteWebDriver webDriver = new RemoteWebDriver(chrome.getSeleniumAddress(), new ChromeOptions());

        ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

        // Synchronize user via SCIM
        User user = new User();
        user.setUserName("test.user1");
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName("Test", "User 1"));
        user.setEmails(getEmails("test.user1@org1.example.com"));
        user.putAdditionalProperty("externalId", "97d3bd9b-73ef-440e-80fb-795ad2b8086a");
        assertNotNull(scimClient.createUser(user));

        // Log in with external identity provider

        loginExternalIdp(
            webDriver,
            TestConsts.ORGANIZATIONS_REALM,
            TestConsts.EXTERNAL_USER_1_USERNAME,
            TestConsts.EXTERNAL_USER_1_PASSWORD
        );

        // Assert that the user is logged in

        waitAndAssertInputValue(webDriver, By.id("username"), "test.user1");
        waitAndAssertInputValue(webDriver, By.id("email"), "test.user1@org1.example.com");
    }

}
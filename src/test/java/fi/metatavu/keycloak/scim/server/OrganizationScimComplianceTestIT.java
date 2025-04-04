package fi.metatavu.keycloak.scim.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCIM compliance tests
 */
@Testcontainers
public class OrganizationScimComplianceTestIT extends AbstractOrganizationScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
        .withNetwork(network)
        .withNetworkAliases("scim-keycloak")
        .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
        .withRealmImportFiles("kc-organizations.json", "kc-external.json")
        .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));

    @Container
    @SuppressWarnings({"try", "resource"})
    private static final GenericContainer<?> scimCompliance = new GenericContainer<>("suvera/scim2-compliance-test-utility:1.0.2")
        .withExposedPorts(8081)
        .withNetwork(network)
        .withNetworkAliases("scim-tester")
        .waitingFor(Wait.forLogMessage(".*Started Scim2Application.*", 1))
        .withLogConsumer(outputFrame -> System.out.printf("COMPLIANCE-TEST: %s", outputFrame.getUtf8String()));

    @Override
    protected KeycloakContainer getKeycloakContainer() {
        return keycloakContainer;
    }

    @Test
    void scimComplianceShouldPass() throws IOException, InterruptedException {
        URI complianceServerUrl = URI.create(String.format("http://%s:%d", scimCompliance.getHost(), scimCompliance.getMappedPort(8081)));
        URI endPointUrl = URI.create(String.format("http://scim-keycloak:8080/realms/%s/scim/v2/organizations/%s/", TestConsts.ORGANIZATIONS_REALM, TestConsts.ORGANIZATION_1_ID));
        String accessToken = getExternalServiceAccountToken();

        System.out.println("accessToken: " + accessToken);

        String runId = startComplianceTests(complianceServerUrl, endPointUrl, accessToken, true, false);

        await()
            .atMost(Duration.ofMinutes(1))
            .until(() -> {
                ComplianceStatus status = getComplianceStatus(complianceServerUrl, runId);
                return !status.data.isEmpty() && Objects.equals(status.data.getLast().title, "--DONE--");
            });

        List<ComplianceTestStatus> testStatuses = getComplianceStatus(complianceServerUrl, runId).data
            .stream().filter(testStatus -> !Objects.equals(testStatus.title, "--DONE--"))
            .toList();

        for (ComplianceTestStatus testStatus : testStatuses) {
            assertTrue(testStatus.success, "Compliance test failed: " + testStatus.title + "\n" + new ObjectMapper().writeValueAsString(testStatus));
        }
    }

}

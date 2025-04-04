package fi.metatavu.keycloak.scim.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.Container.ExecResult;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * SCIM compliance tests
 */
@Testcontainers
public class RealmScimComplianceTestIT extends AbstractRealmScimTest {

    @Container
    protected static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
        .withNetwork(network)
        .withNetworkAliases("scim-keycloak")
        .withEnv("SCIM_AUTHENTICATION_MODE", "KEYCLOAK")
        .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
        .withRealmImportFile("kc-test.json")
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
        URI endPointUrl = URI.create("http://scim-keycloak:8080/realms/test/scim/v2");
        String accessToken = getAccessToken();
        String runId = startComplianceTests(complianceServerUrl, endPointUrl, accessToken, true, true);

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

    /**
     * Fetches access token from Keycloak, using the compliance tester container
     *
     * @return access token
     */
    public String getAccessToken() throws IOException, InterruptedException {
        String curlCommand = String.format(
            "curl -s -X POST %s -d \"grant_type=client_credentials\" -d \"client_id=%s\" -d \"client_secret=%s\"",
            "http://scim-keycloak:8080/realms/test/protocol/openid-connect/token",
            TestConsts.TEST_SCIM_CLIENT_ID,
            TestConsts.TEST_SCIM_CLIENT_SECRET
        );

        ExecResult execResult = scimCompliance.execInContainer("sh", "-c", curlCommand);

        if (execResult.getExitCode() != 0) {
            throw new RuntimeException("Token fetch failed: " + execResult.getStderr());
        }

        String stdout = execResult.getStdout();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(stdout);

        if (!node.has("access_token")) {
            throw new RuntimeException("access_token not found in response: " + stdout);
        }

        return node.get("access_token").asText();
    }

}

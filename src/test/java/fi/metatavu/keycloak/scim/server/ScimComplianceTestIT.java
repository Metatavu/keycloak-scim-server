package fi.metatavu.keycloak.scim.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.UriBuilder;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SCIM compliance tests
 */
@Testcontainers
public class ScimComplianceTestIT extends AbstractScimTest {

    @Container
    @SuppressWarnings("try")
    private static final GenericContainer<?> scimCompliance = new GenericContainer<>("suvera/scim2-compliance-test-utility:1.0.2")
        .withExposedPorts(8081)
        .withNetwork(network)
        .withNetworkAliases("scim-tester")
        .waitingFor(Wait.forLogMessage(".*Started Scim2Application.*", 1))
        .withLogConsumer(outputFrame -> System.out.printf("COMPLIANCE-TEST: %s%n", outputFrame.getUtf8String()));

    @Test
    void scimComplianceShouldPass() throws IOException, InterruptedException {
        URI complianceServerUrl = URI.create(String.format("http://%s:%d", scimCompliance.getHost(), scimCompliance.getMappedPort(8081)));

        String accessToken = getAccessToken();

        String runId = startComplianceTests(complianceServerUrl, accessToken);

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
     * Starts compliance tests in the compliance tester container
     *
     * @param complianceServerUrl compliance tester URL
     * @param accessToken access token
     * @return run ID
     * @throws IOException when the response body can't be read
     * @throws InterruptedException when the HTTP request is interrupted
     */
    private String startComplianceTests(URI complianceServerUrl, String accessToken) throws IOException, InterruptedException {
        URI runUri = UriBuilder.fromUri(complianceServerUrl)
          .path("/test/run")
          .queryParam("endPoint", "http://scim-keycloak:8080/realms/test/scim/v2")
          .queryParam("jwtToken", accessToken)
          .queryParam("usersCheck", 1)
          .queryParam("groupsCheck", 1)
          .queryParam("checkIndResLocation", 1)
          .build();

        ObjectMapper objectMapper = new ObjectMapper();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(runUri)
            .POST(HttpRequest.BodyPublishers.noBody())
            .header("Accept", "application/json")
            .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode responseJson = objectMapper.readTree(response.body());
            return responseJson.get("id").asText();
        }
    }

    /**
     * Fetches compliance test status from the compliance tester container
     *
     * @param complianceServerUrl compliance tester URL
     * @param runId run ID
     * @return compliance test status
     * @throws IOException when the response body can't be read
     * @throws InterruptedException when the HTTP request is interrupted
     */
    private ComplianceStatus getComplianceStatus(URI complianceServerUrl, String runId) throws IOException, InterruptedException {
        URI statusUri = UriBuilder.fromUri(complianceServerUrl)
          .path("/test/status")
          .queryParam("runId", runId)
          .build();

        ObjectMapper objectMapper = new ObjectMapper();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(statusUri)
            .GET()
            .header("Accept", "application/json")
            .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), ComplianceStatus.class);
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
            TestConsts.SCIM_CLIENT_ID,
            TestConsts.SCIM_CLIENT_SECRET
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

    @SuppressWarnings("unused")
    public static class ComplianceStatus {
        public List<ComplianceTestStatus> data;
        public int nextIndex;
    }

    @SuppressWarnings("unused")
    public static class ComplianceTestStatus {
        public boolean success;
        public boolean notSupported;
        public String title;
        public String requestBody;
        public String requestMethod;
        public String responseBody;
        public int responseCode;
        public Map<String, String[]> responseHeaders;
    }

}

package fi.metatavu.keycloak.scim.server.test.tests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UserEmailsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.UserName;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.AfterEach;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.representations.idm.MemberRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.util.JsonSerialization;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Network;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Abstract base class for SCIM tests
 */
public abstract class AbstractScimTest {

    protected static final Network network = Network.newNetwork();

    @AfterEach
    void clearAdminEventsAfter() throws IOException, InterruptedException {
        clearAdminEvents();
    }

    /**
     * Returns the Keycloak container
     *
     * @return Keycloak container
     */
    protected abstract KeycloakContainer getKeycloakContainer();

    /**
     * Finds user from the test realm
     *
     * @param userId user ID
     * @return user representation
     */
    protected UserRepresentation findRealmUser(String realm, String userId) {
        return getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(realm)
            .users()
            .get(userId)
            .toRepresentation();
    }

    /**
     * Creates a user with the given parameters
     *
     * @param scimClient SCIM client
     * @param userName username
     * @param firstName first name
     * @param lastName last name
     * @return created user
     * @throws ApiException if an error occurs during user creation
     */
    protected User createUser(
        ScimClient scimClient,
        String userName,
        String firstName,
        String lastName
    ) throws ApiException {
        User user = new User();
        user.setUserName(userName);
        user.setActive(true);
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setName(getName(firstName, lastName));
        user.setEmails(getEmails(String.format("%s@example.com", userName)));
        User created = scimClient.createUser(user);
        assertNotNull(created);
        return created;
    }

    /**
     * Creates multiple users with the given parameters
     *
     * @param scimClient SCIM client
     * @param userName username
     * @param firstName first name
     * @param lastName last name
     * @param count number of users to create
     * @return list of created users
     * @throws ApiException if an error occurs during user creation
     */
    @SuppressWarnings("SameParameterValue")
    protected List<User> createUsers(
        ScimClient scimClient,
        String userName,
        String firstName,
        String lastName,
        int count
    ) throws ApiException {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(createUser(scimClient, String.format("%s-%d", userName, i), String.format("%s-%d", firstName, i), String.format("%s-%d", lastName, i)));
        }

        return users;
    }

    /**
     * Finds organization member
     *
     * @param realm realm name
     * @param organizationId organization ID
     * @param userId user ID
     * @return user representation
     */
    @SuppressWarnings("SameParameterValue")
    protected MemberRepresentation findOrganizationMember(String realm, String organizationId, String userId) {
        return getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(realm)
            .organizations()
            .get(organizationId)
            .members()
            .member(userId)
            .toRepresentation();
    }

    /**
     * Lists user realm role mappings
     *
     * @param userId user ID
     * @return user realm role mappings
     */
    protected List<RoleRepresentation> getUserRealmRoleMappings(String realm, String userId) {
        return getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(realm)
            .users()
            .get(userId)
            .roles()
            .getAll()
            .getRealmMappings();
    }

    /**
     * Creates a new organization member in Keycloak
     *
     * @param realm realm name
     * @param organizationId organization ID
     * @param newUser new user representation to create
     * @return user ID of the created user
     */
    @SuppressWarnings("SameParameterValue")
    protected String createOrganizationMember(
            String realm,
            String organizationId,
            UserRepresentation newUser
    ) {
        String userId;

        try (Response response = getKeycloakContainer()
                .getKeycloakAdminClient()
                .realm(realm)
                .users()
                .create(newUser)) {
            assertEquals(201, response.getStatus());

            String location = response.getHeaderString("Location");
            userId = location.substring(location.lastIndexOf("/") + 1);
        }

        try (Response response = getKeycloakContainer()
                .getKeycloakAdminClient()
                .realm(realm)
                .organizations()
                .get(organizationId)
                .members()
                .addMember(userId)) {
            assertEquals(201, response.getStatus());
        }

        return userId;
    }

    /**
     * Deletes user from Keycloak
     *
     * @param userId user ID
     */
    protected void deleteRealmUser(String realm, String userId) {
        getKeycloakContainer().getKeycloakAdminClient()
            .realms()
            .realm(realm)
            .users()
            .get(userId)
            .remove();
    }

    /**
     * Deletes users from Keycloak
     *
     * @param users users to delete
     */
    @SuppressWarnings("SameParameterValue")
    protected void deleteRealmUsers(String realm, List<User> users) {
        for (User user : users) {
            deleteRealmUser(realm, user.getId());
        }
    }

    /**
     * Asserts that two users are equal
     *
     * @param expected expected user
     * @param actual actual user
     */
    protected void assertUserEquals(User expected, User actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getUserName(), actual.getUserName());
        assertEquals(expected.getName() != null ? expected.getName().getGivenName() : null, actual.getName() != null ? actual.getName().getGivenName() : null);
        assertEquals(expected.getName() != null ? expected.getName().getFamilyName() : null, actual.getName() != null ? actual.getName().getFamilyName() : null);
        assertEquals(expected.getEmails() != null && !expected.getEmails().isEmpty() ? expected.getEmails().getFirst().getValue() : null, actual.getEmails() != null && !actual.getEmails().isEmpty() ? actual.getEmails().getFirst().getValue() : null);
        assertEquals(expected.getAdditionalProperties(), actual.getAdditionalProperties());

        if (expected.getAdditionalProperties() != null) {
            expected.getAdditionalProperties().forEach((key, value) -> assertEquals(value, actual.getAdditionalProperty(key)));
        }
    }

    /**
     * Asserts user
     *
     * @param user user
     * @param expectedId expected ID
     * @param expectedUserName expected username
     * @param expectedGivenName expected given name
     * @param expectedFamilyName expected family name
     * @param expectedEmail expected email
     */
    protected void assertUser(
        User user,
        String expectedId,
        String expectedUserName,
        String expectedGivenName,
        String expectedFamilyName,
        String expectedEmail,
        String expectedExternalId,
        String expectedPreferredLanguage,
        String expectedDisplayName
    ) {
        assertNotNull(user.getId());
        assertNotNull(user.getName());
        assertNotNull(user.getEmails());

        assertEquals(expectedId, user.getId());
        assertEquals(expectedUserName, user.getUserName());
        assertEquals(expectedGivenName, user.getName().getGivenName());
        assertEquals(expectedFamilyName, user.getName().getFamilyName());
        assertEquals(1, user.getEmails().size());
        assertEquals(expectedEmail, user.getEmails().getFirst().getValue());

        assertEquals(expectedExternalId, user.getAdditionalProperty("externalId"));
        assertEquals(expectedPreferredLanguage, user.getAdditionalProperty("preferredLanguage"));
        assertEquals(expectedDisplayName, user.getAdditionalProperty("displayName"));
    }

    /**
     * Creates a UserName object for SCIM user
     *
     * @param givenName given name
     * @param familyName family name
     * @return UserName object
     */
    @SuppressWarnings("SameParameterValue")
    protected UserName getName(
            String givenName,
            String familyName
    ) {
        UserName result = new UserName();
        result.setGivenName(givenName);
        result.setFamilyName(familyName);
        return result;
    }

    /**
     * Returns a list of email addresses for SCIM user
     *
     * @param value email address
     * @return list of email addresses
     */
    @SuppressWarnings("SameParameterValue")
    protected List<UserEmailsInner> getEmails(String value) {
        UserEmailsInner result = new UserEmailsInner();
        result.setValue(value);
        return Collections.singletonList(result);
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
    @SuppressWarnings("SameParameterValue")
    protected String startComplianceTests(
        URI complianceServerUrl,
        URI endPointUrl,
        String accessToken,
        boolean usersCheck,
        boolean groupsCheck
    ) throws IOException, InterruptedException {
        URI runUri = UriBuilder.fromUri(complianceServerUrl)
            .path("/test/run")
            .queryParam("endPoint", endPointUrl)
            .queryParam("jwtToken", accessToken)
            .queryParam("usersCheck", usersCheck ? 1 : 0)
            .queryParam("groupsCheck", groupsCheck ? 1 : 0)
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
    protected ComplianceStatus getComplianceStatus(URI complianceServerUrl, String runId) throws IOException, InterruptedException {
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
            String responseBody = response.body();
            try {
                return objectMapper.readValue(responseBody, ComplianceStatus.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse compliance status response: " + responseBody, e);
            }
        }
    }

    /**
     * Returns admin events that have been recorded during the test execution
     *
     * @return list of admin events
     */
    protected List<AdminEvent> getAdminEvents() throws IOException {
        Path testData = Files.createTempDirectory("testdata");

        try {
            String containerDir = "/tmp/testdata/admin-events";
            Container.ExecResult result = getKeycloakContainer().execInContainer("sh", "-c", "ls " + containerDir + "/*.json");
            if (result.getExitCode() != 0) {
                throw new RuntimeException("Failed to list files: " + result.getStderr());
            }

            String[] containerFiles = result.getStdout().split("\n");

            for (String containerFile : containerFiles) {
                String fileName = containerFile.substring(containerFile.lastIndexOf("/") + 1);
                Path destPath = testData.resolve(fileName);
                getKeycloakContainer().copyFileFromContainer(containerFile, destPath.toString());
            }

            File[] files = testData.toFile().listFiles();
            assertNotNull(files, "Admin events directory is empty or not accessible");

            return Arrays.stream(files)
                .map(file -> {
                    try (FileInputStream fileInputStream = new FileInputStream(file)) {
                        return JsonSerialization.readValue(fileInputStream, AdminEvent.class);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read admin event from file: " + file.getAbsolutePath(), e);
                    }
                })
                .toList();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Clears admin events recorded during the test execution
     */
    protected void clearAdminEvents() throws IOException {
        try {
            getKeycloakContainer().execInContainer(
                "sh", "-c",
                "rm -rf /tmp/testdata/admin-events"
            );
        } catch (Exception e) {
            throw new IOException("Failed to clear admin events", e);
        }
    }

    /**
     * Asserts that the given user admin event matches the expected values
     *
     * @param userEvent the user admin event to assert
     * @param realmName the name of the realm
     * @param realmId the ID of the realm
     * @param userId the ID of the user
     * @param operationType the operation type of the event
     * @throws IOException if there is an error reading the user representation
     */
    @SuppressWarnings("SameParameterValue")
    protected void assertUserAdminEvent(
            AdminEvent userEvent,
            String realmName,
            String realmId,
            String userId,
            OperationType operationType
    ) throws IOException {
        assertNotNull(userEvent);
        assertEquals(realmId, userEvent.getRealmId());
        assertEquals(realmName, userEvent.getRealmName());
        assertEquals(ResourceType.USER, userEvent.getResourceType());
        assertEquals(operationType, userEvent.getOperationType());
        assertEquals("users/" + userId, userEvent.getResourcePath());
        assertEquals("USER", userEvent.getResourceTypeAsString());

        if (operationType != OperationType.DELETE) {
            UserRepresentation realmUser = findRealmUser(realmName, userId);

            UserRepresentation eventUser = JsonSerialization.readValue(userEvent.getRepresentation(), UserRepresentation.class);
            assertNotNull(eventUser);

            assertEquals(realmUser.getId(), eventUser.getId());
            assertEquals(realmUser.getUsername(), eventUser.getUsername());
            assertEquals(realmUser.getFirstName(), eventUser.getFirstName());
            assertEquals(realmUser.getLastName(), eventUser.getLastName());
            assertEquals(realmUser.getEmail(), eventUser.getEmail());
            assertEquals(realmUser.isEnabled(), eventUser.isEnabled());

            if (realmUser.getAttributes() == null) {
                assertNull(eventUser.getAttributes());
            } else {
                assertNotNull(eventUser.getAttributes());
                realmUser.getAttributes().forEach((key, values) -> {
                    assertEquals(values, eventUser.getAttributes().get(key));
                });
            }
        }
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComplianceStatus {
        public List<ComplianceTestStatus> data;
        public int nextIndex;
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
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

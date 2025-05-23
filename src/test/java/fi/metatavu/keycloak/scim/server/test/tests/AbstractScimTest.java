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
import jakarta.ws.rs.core.UriBuilder;
import org.keycloak.representations.idm.MemberRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.Network;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Abstract base class for SCIM tests
 */
public abstract class AbstractScimTest {

    protected static final Network network = Network.newNetwork();

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

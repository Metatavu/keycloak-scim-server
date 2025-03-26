package fi.metatavu.keycloak.scim.server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UsersList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User list endpoint
 */
@Testcontainers
class UserListTestsIT {

  private static final Network network = Network.newNetwork();

  @Container
  private static final KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.1.2")
      .withNetwork(network)
      .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
      .withRealmImportFile("kc-test.json")
      .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));

  @BeforeAll
  static void setUp() {
    assertTrue(keycloak.isRunning());
  }

  @Test
  void testListUsersNoFilter() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();

    UsersList usersList = scimClient.listUsers(null, 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertEquals(0, usersList.getStartIndex());
    assertEquals(10, usersList.getItemsPerPage());

    List<User> users = usersList.getResources();
    assertNotNull(users);
    assertEquals(1, users.size());

    assertUser(
        users.getFirst(),
        "6794995e-e862-4208-aadf-5f0bf411b29d",
        "testadmin",
        "Test",
        "Admin",
        "testadmin@example.com"
    );
  }

  @Test
  void testFilterByUserName() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("userName eq \"testadmin\"", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertEquals("testadmin", usersList.getResources().getFirst().getUserName());
  }

  @Test
  void testFilterByEmail() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("email eq \"testadmin@example.com\"", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertNotNull(usersList.getResources().getFirst().getName());
    assertNotNull(usersList.getResources().getFirst().getEmails());
    assertEquals("testadmin@example.com", usersList.getResources().getFirst().getEmails().getFirst().getValue());
  }

  @Test
  void testFilterByFirstName() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("firstName eq \"Test\"", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertNotNull(usersList.getResources().getFirst().getName());
    assertEquals("Test", usersList.getResources().getFirst().getName().getGivenName());
  }

  @Test
  void testFilterByLastName() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("lastName eq \"Admin\"", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertNotNull(usersList.getResources().getFirst().getName());
    assertEquals("Admin", usersList.getResources().getFirst().getName().getFamilyName());
  }

  @Test
  void testFilterByFamilyName() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("familyName eq \"Admin\"", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertNotNull(usersList.getResources().getFirst().getName());
    assertEquals("Admin", usersList.getResources().getFirst().getName().getFamilyName());
  }

  @Test
  void testFilterByActiveTrue() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("active eq true", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertNotNull(usersList.getResources().getFirst().getActive());
    assertTrue(usersList.getResources().getFirst().getActive());
  }

  @Test
  void testFilterByActiveFalseReturnsEmpty() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("active eq false", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByUserNameAndActive() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("userName eq \"testadmin\" and active eq true", 0, 10);

    assertEquals(1, usersList.getTotalResults());
  }

  @Test
  void testFilterByUserNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("userName eq \"nonexistent\"", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByEmailNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("email eq \"nope@example.com\"", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByFirstNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("firstName eq \"NoSuchName\"", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByLastNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("lastName eq \"Unknown\"", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByFamilyNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("familyName eq \"Ghost\"", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByActiveFalseNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("active eq false", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testInvalidFilterMissingOperator() {
    ScimClient scimClient = getAuthenticatedScimClient();

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName \"bob\"", 0, 10)
    );

    assertEquals("listUsers call failed with: 400 - Invalid filter", exception.getMessage());
  }

  @Test
  void testInvalidFilterUnsupportedOperator() {
    ScimClient scimClient = getAuthenticatedScimClient();

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName gt \"bob\"", 0, 10)
    );

    assertEquals("listUsers call failed with: 400 - Invalid filter", exception.getMessage());
  }

  @Test
  void testInvalidFilterUnquotedString() {
    ScimClient scimClient = getAuthenticatedScimClient();

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName eq bob", 0, 10)
    );

    assertEquals("listUsers call failed with: 400 - Invalid filter", exception.getMessage());
  }

  @Test
  void testInvalidFilterBadLogicalStructure() {
    ScimClient scimClient = getAuthenticatedScimClient();

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName eq \"a\" and", 0, 10)
    );

    assertEquals("listUsers call failed with: 400 - Invalid filter", exception.getMessage());
  }

  @SuppressWarnings("SameParameterValue")
  private static void assertUser(
      User user,
      String expectedId,
      String expectedUserName,
      String expectedGivenName,
      String expectedFamilyName,
      String expectedEmail
  ) {
    assertNotNull(user.getName());
    assertNotNull(user.getEmails());

    assertEquals(expectedId, user.getId());
    assertEquals(expectedUserName, user.getUserName());
    assertEquals(expectedGivenName, user.getName().getGivenName());
    assertEquals(expectedFamilyName, user.getName().getFamilyName());
    assertEquals(1, user.getEmails().size());
    assertEquals(expectedEmail, user.getEmails().getFirst().getValue());
  }

  /**
   * Returns service account token
   *
   * @return service account token
   */
  private String getServiceAccountToken() {
      try (Keycloak keycloakAdmin = KeycloakBuilder.builder()
            .serverUrl(keycloak.getAuthServerUrl())
            .realm(TestConsts.REALM)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(TestConsts.SCIM_CLIENT_ID)
            .clientSecret(TestConsts.SCIM_CLIENT_SECRET)
            .build()) {

          return keycloakAdmin
              .tokenManager()
              .getAccessToken()
              .getToken();
      }
  }

  /**
   * Returns SCIM URI for the test realm
   *
   * @return SCIM URI
   */
  private URI getScimUri() {
    return URI.create(keycloak.getAuthServerUrl()).resolve(String.format("/realms/%s/scim/v2/", TestConsts.REALM));
  }

  /**
   * Returns authenticated SCIM client
   *
   * @return authenticated SCIM client
   */
  private ScimClient getAuthenticatedScimClient() {
    return new ScimClient(getScimUri(), getServiceAccountToken());
  }

}

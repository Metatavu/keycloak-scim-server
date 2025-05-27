package fi.metatavu.keycloak.scim.server.test.tests.functional;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.scim.server.test.tests.AbstractOrganizationScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.test.client.model.PatchRequestOperationsInner;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UsersList;
import fi.metatavu.keycloak.scim.server.test.utils.KeycloakTestUtils;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User list endpoint
 */
@Testcontainers
class OrganizationUserListTestsIT extends AbstractOrganizationScimTest {

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
  void testListUsersNoFilter() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

    User user = createUser(scimClient, "list-me", "List", "Me");

    UsersList usersList = scimClient.listUsers(null, 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertEquals(0, usersList.getStartIndex());
    assertEquals(10, usersList.getItemsPerPage());

    List<User> users = usersList.getResources();
    assertNotNull(users);
    assertEquals(1, users.size());
    assertUserEquals(user, users.getFirst());

    deleteRealmUser(TestConsts.ORGANIZATIONS_REALM, user.getId());
  }

  @Test
  void testFilterByUserName() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

    List<User> users = createUsers(scimClient, "list-me", "List", "Me", 5);

    UsersList usersList = scimClient.listUsers("userName eq \"list-me-1\"", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertEquals("list-me-1", usersList.getResources().getFirst().getUserName());

    deleteRealmUsers(TestConsts.ORGANIZATIONS_REALM, users);
  }

  @Test
  void testFilterByEmail() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    List<User> users = createUsers(scimClient, "list-me", "List", "Me", 5);

    UsersList usersList = scimClient.listUsers("email eq \"list-me-1@example.com\"", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertNotNull(usersList.getResources().getFirst().getName());
    assertNotNull(usersList.getResources().getFirst().getEmails());
    assertEquals("list-me-1@example.com", usersList.getResources().getFirst().getEmails().getFirst().getValue());

    deleteRealmUsers(TestConsts.ORGANIZATIONS_REALM, users);
  }

  @Test
  void testFilterByGivenName() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    List<User> users = createUsers(scimClient, "list-me", "List", "Me", 5);
    UsersList usersList = scimClient.listUsers("name.givenName eq \"List-1\"", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertNotNull(usersList.getResources().getFirst().getName());
    assertEquals("List-1", usersList.getResources().getFirst().getName().getGivenName());

    deleteRealmUsers(TestConsts.ORGANIZATIONS_REALM, users);
  }

  @Test
  void testFilterByFamilyName() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    List<User> users = createUsers(scimClient, "list-me", "List", "Me", 5);
    UsersList usersList = scimClient.listUsers("name.familyName eq \"Me-1\"", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertNotNull(usersList.getResources().getFirst().getName());
    assertEquals("Me-1", usersList.getResources().getFirst().getName().getFamilyName());
    deleteRealmUsers(TestConsts.ORGANIZATIONS_REALM, users);
  }

  @Test
  void testFilterByActiveTrue() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

    List<User> users = createUsers(scimClient, "list-me", "List", "Me", 5);

    scimClient.patchUser(users.getFirst().getId(), new PatchRequest()
        .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
        .operations(List.of(
              new PatchRequestOperationsInner()
                  .op("Replace")
                  .path("active")
                  .value(Boolean.FALSE)
        )));

    UsersList usersList = scimClient.listUsers("active eq true", 0, 10);

    assertEquals(4, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(4, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertNotNull(usersList.getResources().getFirst().getActive());

    for (User user : usersList.getResources()) {
      assertNotNull(user.getActive());
      assertTrue(user.getActive());
    }

    deleteRealmUsers(TestConsts.ORGANIZATIONS_REALM, users);
  }

  @Test
  void testFilterByActiveFalseReturnsEmpty() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    UsersList usersList = scimClient.listUsers("active eq false", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByUserNameAndActive() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

    List<User> users = createUsers(scimClient, "list-me", "List", "Me", 5);

    scimClient.patchUser(users.getFirst().getId(), new PatchRequest()
        .schemas(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
        .operations(List.of(
            new PatchRequestOperationsInner()
                .op("Replace")
                .path("active")
                .value(Boolean.FALSE)
        )));

    UsersList usersList = scimClient.listUsers("userName eq \"list-me-1\" and active eq true", 0, 10);

    assertEquals(1, usersList.getTotalResults());
    deleteRealmUsers(TestConsts.ORGANIZATIONS_REALM, users);
  }

  @Test
  void testFilterByUserNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    UsersList usersList = scimClient.listUsers("userName eq \"nonexistent\"", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByEmailNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    UsersList usersList = scimClient.listUsers("email eq \"nope@example.com\"", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByGivenNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    UsersList usersList = scimClient.listUsers("name.givenName eq \"NoSuchName\"", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByFamilyNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    UsersList usersList = scimClient.listUsers("name.familyName eq \"Ghost\"", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByActiveFalseNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    UsersList usersList = scimClient.listUsers("active eq false", 0, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testInvalidFilterMissingOperator() {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName \"bob\"", 0, 10)
    );

    assertEquals("listUsers call failed with: 400 - Invalid filter", exception.getMessage());
  }

  @Test
  void testInvalidFilterUnsupportedOperator() {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName gt \"bob\"", 0, 10)
    );

    assertEquals("listUsers call failed with: 400 - Invalid filter", exception.getMessage());
  }

  @Test
  void testInvalidFilterUnquotedString() {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName eq bob", 0, 10)
    );

    assertEquals("listUsers call failed with: 400 - Invalid filter", exception.getMessage());
  }

  @Test
  void testInvalidFilterBadLogicalStructure() {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName eq \"a\" and", 0, 10)
    );

    assertEquals("listUsers call failed with: 400 - Invalid filter", exception.getMessage());
  }

  @Test
  void testPagination() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_1_ID);
    List<User> createdUsers = new ArrayList<>();

    // Create 5 users
    for (int i = 1; i <= 5; i++) {
      createdUsers.add(createUser(scimClient, "paginated-user-" + i, "Paginated", "User" + i));
    }

    // Page 1: count=2, startIndex=0
    UsersList page1 = scimClient.listUsers("name.givenName eq \"Paginated\"", 0, 2);
    assertEquals(2, page1.getItemsPerPage());
    assertEquals(0, page1.getStartIndex());
    assertEquals(5, page1.getTotalResults());
    assertNotNull(page1.getResources());
    assertEquals(2, page1.getResources().size());

    // Page 2: count=2, startIndex=2
    UsersList page2 = scimClient.listUsers("name.givenName eq \"Paginated\"", 2, 2);
    assertEquals(2, page2.getItemsPerPage());
    assertEquals(2, page2.getStartIndex());
    assertEquals(5, page2.getTotalResults());
    assertNotNull(page2.getResources());
    assertEquals(2, page2.getResources().size());

    // Page 3: count=2, startIndex=4 (only one user expected)
    UsersList page3 = scimClient.listUsers("name.givenName eq \"Paginated\"", 4, 2);
    assertEquals(2, page3.getItemsPerPage());
    assertEquals(4, page3.getStartIndex());
    assertEquals(5, page3.getTotalResults());
    assertNotNull(page3.getResources());
    assertTrue(page3.getResources().size() <= 2);

    // Cleanup
    deleteRealmUsers(TestConsts.ORGANIZATIONS_REALM, createdUsers);
  }

  @Test
  void testListUsersEmailAsUsername() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_ID);

    UsersList usersList = scimClient.listUsers(null, 0, 10);
    assertNotNull(usersList);
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getTotalResults());
    assertEquals("existing-user@example.com", usersList.getResources().getFirst().getUserName());
  }

  @Test
  void testFilterUsersEmailAsUsername() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient(TestConsts.ORGANIZATION_EMAIL_AS_USERNAME_ID);

    UsersList usersByUsername = scimClient.listUsers("userName eq \"existing-user\"", 0, 10);
    assertNotNull(usersByUsername);
    assertNotNull(usersByUsername.getResources());
    assertEquals(0, usersByUsername.getTotalResults());

    UsersList usersByEmail = scimClient.listUsers("userName eq \"existing-user@example.com\"", 0, 10);
    assertNotNull(usersByEmail);
    assertNotNull(usersByEmail.getResources());
    assertEquals(1, usersByEmail.getTotalResults());
    assertEquals("existing-user@example.com", usersByEmail.getResources().getFirst().getUserName());
  }

}

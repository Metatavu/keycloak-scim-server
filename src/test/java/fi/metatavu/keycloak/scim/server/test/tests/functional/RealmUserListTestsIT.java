package fi.metatavu.keycloak.scim.server.test.tests.functional;

import fi.metatavu.keycloak.scim.server.test.tests.AbstractInternalAuthRealmScimTest;
import fi.metatavu.keycloak.scim.server.test.ScimClient;
import fi.metatavu.keycloak.scim.server.test.TestConsts;
import fi.metatavu.keycloak.scim.server.test.client.ApiException;
import fi.metatavu.keycloak.scim.server.test.client.model.User;
import fi.metatavu.keycloak.scim.server.test.client.model.UsersList;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import fi.metatavu.keycloak.scim.server.test.utils.ScimErrorAssertions;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SCIM 2.0 User list endpoint
 */
@Testcontainers
class RealmUserListTestsIT extends AbstractInternalAuthRealmScimTest {

  @Test
  void testListUsersStartIndexLessThanOneTreatedAsOne() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();

    UsersList usersList = scimClient.listUsers(null, 0, 10);

    assertEquals(1, usersList.getStartIndex());
  }

  @Test
  void testListUsersNoFilter() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();

    UsersList usersList = scimClient.listUsers(null, 1, 10);

    assertEquals(1, usersList.getTotalResults());
    assertEquals(1, usersList.getStartIndex());
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
        "testadmin@example.com",
        null,
        null,
        null
    );
  }

  @Test
  void testFilterByUserName() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("userName eq \"testadmin\"", 1, 10);

    assertEquals(1, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertEquals(1, usersList.getResources().size());
    assertNotNull(usersList.getResources().getFirst());
    assertEquals("testadmin", usersList.getResources().getFirst().getUserName());
  }

  @Test
  void testFilterByEmail() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("email eq \"testadmin@example.com\"", 1, 10);

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
    UsersList usersList = scimClient.listUsers("name.givenName eq \"Test\"", 1, 10);

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
    UsersList usersList = scimClient.listUsers("name.familyName eq \"Admin\"", 1, 10);

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
    UsersList usersList = scimClient.listUsers("name.familyName eq \"Admin\"", 1, 10);

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
    UsersList usersList = scimClient.listUsers("active eq true", 1, 10);

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
    UsersList usersList = scimClient.listUsers("active eq false", 1, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByUserNameAndActive() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("userName eq \"testadmin\" and active eq true", 1, 10);

    assertEquals(1, usersList.getTotalResults());
  }

  @Test
  void testFilterByUserNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("userName eq \"nonexistent\"", 1, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByEmailNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("email eq \"nope@example.com\"", 1, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByFirstNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("name.givenName eq \"NoSuchName\"", 1, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByLastNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("name.familyName eq \"Unknown\"", 1, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByFamilyNameNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("name.familyName eq \"Ghost\"", 1, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testFilterByActiveFalseNoMatch() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    UsersList usersList = scimClient.listUsers("active eq false", 1, 10);

    assertEquals(0, usersList.getTotalResults());
    assertNotNull(usersList.getResources());
    assertTrue(usersList.getResources().isEmpty());
  }

  @Test
  void testInvalidFilterMissingOperator() {
    ScimClient scimClient = getAuthenticatedScimClient();

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName \"bob\"", 1, 10)
    );

    ScimErrorAssertions.assertScimError(exception, 400, "Invalid filter");
  }

  @Test
  void testInvalidFilterUnsupportedOperator() {
    ScimClient scimClient = getAuthenticatedScimClient();

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName gt \"bob\"", 1, 10)
    );

    ScimErrorAssertions.assertScimError(exception, 400, "Invalid filter");
  }

  @Test
  void testInvalidFilterUnquotedString() {
    ScimClient scimClient = getAuthenticatedScimClient();

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName eq bob", 1, 10)
    );

    ScimErrorAssertions.assertScimError(exception, 400, "Invalid filter");
  }

  @Test
  void testInvalidFilterBadLogicalStructure() {
    ScimClient scimClient = getAuthenticatedScimClient();

    ApiException exception = assertThrows(ApiException.class, () ->
            scimClient.listUsers("userName eq \"a\" and", 1, 10)
    );

    ScimErrorAssertions.assertScimError(exception, 400, "Invalid filter");
  }

  @Test
  void testPagination() throws ApiException {
    ScimClient scimClient = getAuthenticatedScimClient();
    List<User> createdUsers = new ArrayList<>();

    // Create 5 users
    for (int i = 1; i <= 5; i++) {
      User user = new User();
      user.setUserName("paginated-user-" + i);
      user.setActive(true);
      user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
      user.setName(getName("Paginated", "User" + i));
      user.setEmails(getEmails("pagination" + i + "@example.com"));

      User created = scimClient.createUser(user);
      createdUsers.add(created);
    }

    // Page 1: count=2, startIndex=1
    UsersList page1 = scimClient.listUsers("name.givenName eq \"Paginated\"", 1, 2);
    assertEquals(2, page1.getItemsPerPage());
    assertEquals(1, page1.getStartIndex());
    assertEquals(5, page1.getTotalResults());
    assertNotNull(page1.getResources());
    assertEquals(2, page1.getResources().size());

    // Page 2: count=2, startIndex=3
    UsersList page2 = scimClient.listUsers("name.givenName eq \"Paginated\"", 3, 2);
    assertEquals(2, page2.getItemsPerPage());
    assertEquals(3, page2.getStartIndex());
    assertEquals(5, page2.getTotalResults());
    assertNotNull(page2.getResources());
    assertEquals(2, page2.getResources().size());

    // Page 3: count=2, startIndex=5 (only one user expected)
    UsersList page3 = scimClient.listUsers("name.givenName eq \"Paginated\"", 5, 2);
    assertEquals(2, page3.getItemsPerPage());
    assertEquals(5, page3.getStartIndex());
    assertEquals(5, page3.getTotalResults());
    assertNotNull(page3.getResources());
    assertTrue(page3.getResources().size() <= 2);

    // Cleanup
    for (User user : createdUsers) {
      deleteRealmUser(TestConsts.TEST_REALM, user.getId());
    }
  }

}

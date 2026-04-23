package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.ScimContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.net.URI;
import java.util.stream.Stream;

/**
 * SCIM context for organizations. Extended to allow hiding of which organization
 * implementation is being used.
 */
public abstract class OrganizationScimContext extends ScimContext {

  protected final String organizationId;
  
  /**
   * Constructor
   *
   * @param baseUri base URI
   * @param session keycloak session
   * @param realm realm
   * @param organizationId organizationId
   * @param config organization scim config
   */
  public OrganizationScimContext(URI baseUri, KeycloakSession session, RealmModel realm, String organizationId, OrganizationScimConfig config) {
    super(baseUri, session, realm, config);
    this.organizationId = organizationId;
  }

  /**
   * Gets the organizationId
   *
   * @return organizationId
   */
  public String getOrganizationId() {
    return organizationId;
  }

  /**
   * Get a paginated stream of this organization's users
   *
   * @return Stream of UserModel 
   */
  public abstract Stream<UserModel> getMembersStream(Integer first, Integer max);

  /**
   * Find the user that is a member of this organization given the userId
   *
   * @return UserModel found user, or null
   */
  public abstract UserModel findUser(String userId);

  /**
   * Add a member to this organization
   *
   * @return true if the user was successfully added as a member
   */
  public abstract boolean addMember(UserModel user);

  /**
   * Checks if the user is a member of this organization
   *
   * @return true if the user is a member
   */
  public abstract boolean isMember(UserModel user);

  /**
   * Remove a member from this organization
   *
   * @return true if the user was successfully removed as a member
   */
  public abstract boolean removeMember(UserModel user);

  /**
   * Link the user to this organization's first IdP
   *
   * @return true if the user was successfully linked
   */ 
  public abstract boolean linkUserIdp(UserModel user, String scimUserEmail, String scimUserName, String scimExternalId);
 
  /**
   * Gets the organization representation suitable for serializaing to JSON
   * for admin events or REST responses
   *
   * @return organization
   */
  public abstract Object toRepresentation();
}

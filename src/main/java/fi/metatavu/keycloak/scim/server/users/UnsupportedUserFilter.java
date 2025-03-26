package fi.metatavu.keycloak.scim.server.users;

/**
 * Exception thrown when SCIM user filter is unsupported or invalid
 */
public class UnsupportedUserFilter extends RuntimeException {

  private final String filter;

  /**
   * Constructor
   *
   * @param filter filter
   */
  public UnsupportedUserFilter(String filter) {
    super("Unsupported or invalid SCIM user filter: " + filter);
    this.filter = filter;
  }

  /**
   * Returns filter
   *
   * @return filter
   */
  public String getFilter() {
    return filter;
  }

}

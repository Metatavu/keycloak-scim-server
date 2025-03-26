package fi.metatavu.keycloak.scim.server.filter;

/**
 * Unsupported SCIM filter
 * <p>
 * This class is responsible for unsupported SCIM filters
 */
public class UnsupportedFilter extends RuntimeException {

  private final String filter;

  /**
   * Constructor
   *
   * @param filter filter
   */
  public UnsupportedFilter(String filter) {
    super("Unsupported or invalid SCIM filter: " + filter);
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

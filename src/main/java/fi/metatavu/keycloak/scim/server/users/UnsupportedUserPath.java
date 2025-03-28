package fi.metatavu.keycloak.scim.server.users;

/**
 * Exception thrown when SCIM user path is unsupported or invalid
 */
public class UnsupportedUserPath extends RuntimeException {

  private final String path;

  /**
   * Constructor
   *
   * @param path path
   */
  public UnsupportedUserPath(String path) {
    super("Unsupported or invalid SCIM user filter: " + path);
    this.path = path;
  }

  public String getPath() {
    return path;
  }
}

package fi.metatavu.keycloak.scim.server.attributes;

/**
 * Thrown when a request carries both "attributes" and "excludedAttributes".
 *
 * <p>RFC 7644 §3.9 defines the two parameters as mutually exclusive, so the
 * server cannot resolve which set the client meant.
 */
public class ConflictingAttributeSelection extends Exception {

    public ConflictingAttributeSelection() {
        super("The attributes and excludedAttributes parameters are mutually exclusive");
    }
}

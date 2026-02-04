package fi.metatavu.keycloak.scim.server.authentication;

/**
 * Interface to verify authentication
 */
public interface Verifier {

    /**
     * Verifies the given token.
     *
     * @param tokenString JWT or shared token string
     * @return true if the token is valid, false otherwise
     */
    boolean verify(String tokenString);

}

package fi.metatavu.keycloak.scim.server.authentication;

import java.security.PublicKey;

/**
 * JWK key
 */
public class JwkKey {

    private final PublicKey publicKey;
    private final String kid;
    private final String use;

    /**
     * Constructor
     *
     * @param publicKey public key
     * @param kid key id
     * @param use use of the key
     */
    JwkKey(PublicKey publicKey, String kid, String use) {
        this.publicKey = publicKey;
        this.kid = kid;
        this.use = use;
    }

    /**
     * Returns the public key
     *
     * @return public key
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Returns the key id
     *
     * @return key id
     */
    public String getKid() {
        return kid;
    }

    /**
     * Returns the use of the key
     *
     * @return use of the key
     */
    public String getUse() {
        return use;
    }
}

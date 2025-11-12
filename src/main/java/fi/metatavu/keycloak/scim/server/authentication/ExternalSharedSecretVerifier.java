package fi.metatavu.keycloak.scim.server.authentication;

import org.jboss.logging.Logger;

import static org.apache.commons.codec.digest.DigestUtils.sha512Hex;

/**
 * Verifies shared secret
 */
public class ExternalSharedSecretVerifier implements Verifier {

    private static final Logger logger = Logger.getLogger(ExternalSharedSecretVerifier.class);

    private final String sharedSecret;

    /**
     * Constructor
     *
     * @param sharedSecret shared secret
     */
    public ExternalSharedSecretVerifier(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    /**
     * Verifies the given token.
     *
     * @param tokenString shared token string
     * @return true if the token is valid, false otherwise
     */
    public boolean verify(String tokenString) {
        if (sharedSecret != null && !sharedSecret.isBlank()) {
            String hashed = sha512Hex(tokenString);
            return sharedSecret.equalsIgnoreCase(hashed);
        }

        logger.warn("Shared secret is null or blank.");
        return false;
    }

}

package fi.metatavu.keycloak.scim.server.authentication;

import org.jboss.logging.Logger;
import org.keycloak.jose.jws.JWSInput;

import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.jose.jws.crypto.RSAProvider;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.PublicKey;

/**
 * Verifies externally issued JWT tokens
 */
public class ExternalTokenVerifier {

    private static final Logger logger = Logger.getLogger(ExternalTokenVerifier.class);

    private final String expectedIssuer;
    private final String expectedAudience;
    private final String jwksUrl;

    /**
     * Constructor
     *
     * @param expectedIssuer expected issuer
     * @param jwksUrl JWKS URL
     * @param expectedAudience expected audience
     */
    public ExternalTokenVerifier(String expectedIssuer, String jwksUrl, String expectedAudience) {
        this.expectedIssuer = expectedIssuer;
        this.jwksUrl = jwksUrl;
        this.expectedAudience = expectedAudience;
    }

    /**
     * Verifies the given token.
     *
     * @param tokenString JWT token string
     * @return true if the token is valid, false otherwise
     */
    public boolean verify(String tokenString) throws URISyntaxException, IOException, InterruptedException, JWSInputException {
        for (JwkKey jwkKey : JwksUtils.getPublicKeysFromJwks(jwksUrl)) {
            if (verify(tokenString, jwkKey.getPublicKey())) {
                logger.info("Token verification succeeded with key: " + jwkKey.getKid());
                return true;
            }

            logger.warn("Token verification failed with key: " + jwkKey.getKid());
        }

        return false;
    }

    /**
     * Verifies the given token.
     *
     * @param tokenString JWT token string
     * @return true if the token is valid, false otherwise
     */
    private boolean verify(String tokenString, PublicKey publicKey) throws JWSInputException, IOException {
        JWSInput jwsInput = new JWSInput(tokenString);

        boolean validSignature = RSAProvider.verify(jwsInput, publicKey);

        if (!validSignature) {
            logger.warn("Token signature verification failed");
            return false;
        }

        AccessToken token = JsonSerialization.readValue(jwsInput.getContent(), AccessToken.class);

        if (token == null) {
            logger.warn("Token could not be parsed");
            return false;
        }

        if (!token.getIssuer().equals(expectedIssuer)) {
            if ("*".equals(expectedIssuer)) {
                logger.warn("Token issuer is wildcard, skipping issuer check. This is insecure and should not be used in production. Found issuer is: " + token.getIssuer());
            } else {
                logger.warnf("Token issuer mismatch. Expected: %s, Found: %s", expectedIssuer, token.getIssuer());
                return false;
            }
        }

        if (!arrayContains(token.getAudience(), expectedAudience)) {
            if ("*".equals(expectedAudience)) {
                logger.warn("Token audience is wildcard, skipping audience check. This is insecure and should not be used in production. Found audience is: " + String.join(",", token.getAudience()));
            } else {
                logger.warnf("Token audience mismatch. Expected to contain: %s, Found: %s", expectedAudience, String.join(",", token.getAudience()));
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the given array contains the given value
     *
     * @param array array
     * @param value value
     * @return true if the array contains the value, false otherwise
     */
    private boolean arrayContains(String[] array, String value) {
        for (String s : array) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }

}
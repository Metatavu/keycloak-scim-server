package fi.metatavu.keycloak.scim.server.authentication;

import jakarta.ws.rs.NotAuthorizedException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.PublicKey;
import org.jboss.logging.Logger;
import org.keycloak.common.util.Time;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.jose.jws.crypto.RSAProvider;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JsonSerialization;

/**
 * Verifies externally issued JWT tokens
 */
public class ExternalTokenVerifier implements Verifier {

    private static final Logger logger = Logger.getLogger(ExternalTokenVerifier.class);

    /**
     * Allowed clock skew in seconds between this server and the token issuer when checking exp and nbf
     */
    private static final int ALLOWED_CLOCK_SKEW_SECONDS = 60;

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
    @Override
    public boolean verify(String tokenString) {
        try {
            for (JwkKey jwkKey : JwksUtils.getPublicKeysFromJwks(jwksUrl)) {
                if (verify(tokenString, jwkKey.getPublicKey())) {
                    logger.debug("Token verification succeeded with key: " + jwkKey.getKid());
                    return true;
                }
            }
            logger.warn("Token verification failed with all keys");
        } catch (URISyntaxException | IOException | JWSInputException e) {
            logger.warn("Failed to verify permissions", e);
            throw new NotAuthorizedException(e);
        } catch(InterruptedException e) {
            // InterruptedException should not be ignored
            Thread.currentThread().interrupt();
            logger.warn("Failed to verify permissions", e);
            throw new NotAuthorizedException(e);
        }
        logger.warn("Token verification failed ");
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
            return false;
        }

        AccessToken token = JsonSerialization.readValue(jwsInput.getContent(), AccessToken.class);

        if (token == null) {
            logger.warn("Token could not be parsed");
            return false;
        }

        if (!isWithinValidityPeriod(token)) {
            return false;
        }

        if (token.getIssuer() == null || !token.getIssuer().equals(expectedIssuer)) {
            if ("*".equals(expectedIssuer)) {
                logger.warn("Token issuer is wildcard, skipping issuer check. This is insecure and should not be used in production. Found issuer is: " + token.getIssuer());
            } else {
                logger.warnf("Token issuer mismatch. Expected: %s, Found: %s", expectedIssuer, token.getIssuer());
                return false;
            }
        }

        if (!arrayContains(token.getAudience(), expectedAudience)) {
            if ("*".equals(expectedAudience)) {
                logger.warn("Token audience is wildcard, skipping audience check. This is insecure and should not be used in production. Found audience is: " + formatAudience(token.getAudience()));
            } else {
                logger.warnf("Token audience mismatch. Expected to contain: %s, Found: %s", expectedAudience, formatAudience(token.getAudience()));
                return false;
            }
        }

        return true;
    }

    /**
     * Checks that the token has an expiry time, is not expired and is already valid (nbf),
     * allowing {@link #ALLOWED_CLOCK_SKEW_SECONDS} of clock skew in both directions.
     *
     * @param token token
     * @return true if the token is within its validity period, false otherwise
     */
    private boolean isWithinValidityPeriod(AccessToken token) {
        Long exp = token.getExp();
        if (exp == null || exp <= 0) {
            logger.warn("Token has no expiry time (exp claim), rejecting");
            return false;
        }

        long now = Time.currentTime();
        if (now > exp + ALLOWED_CLOCK_SKEW_SECONDS) {
            logger.warnf("Token has expired. Expired at: %d, current time: %d", exp, now);
            return false;
        }

        if (!token.isNotBefore(ALLOWED_CLOCK_SKEW_SECONDS)) {
            logger.warnf("Token is not valid yet. Not before: %d, current time: %d", token.getNbf(), now);
            return false;
        }

        return true;
    }

    /**
     * Formats token audience for logging
     *
     * @param audience audience
     * @return comma separated audience or "(none)" if the token has no audience
     */
    private String formatAudience(String[] audience) {
        return audience == null ? "(none)" : String.join(",", audience);
    }

    /**
     * Checks if the given array contains the given value
     *
     * @param array array
     * @param value value
     * @return true if the array contains the value, false otherwise
     */
    private boolean arrayContains(String[] array, String value) {
        if (array == null) {
            return false;
        }

        for (String s : array) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }

}

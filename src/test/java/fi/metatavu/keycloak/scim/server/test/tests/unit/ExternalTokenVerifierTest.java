package fi.metatavu.keycloak.scim.server.test.tests.unit;

import com.sun.net.httpserver.HttpServer;
import fi.metatavu.keycloak.scim.server.authentication.ExternalTokenVerifier;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.common.util.Time;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKBuilder;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JsonSerialization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ExternalTokenVerifier}.
 *
 * Tokens are signed with a locally generated RSA key whose JWKS is served from a local HTTP server,
 * so the test can control every claim (exp, nbf, iss, aud).
 */
class ExternalTokenVerifierTest {

    private static final String KID = "test-key";
    private static final String ISSUER = "https://issuer.example.com";
    private static final String AUDIENCE = "scim";

    private static KeyPair keyPair;
    private static HttpServer jwksServer;
    private static String jwksUrl;

    @BeforeAll
    static void setUp() throws Exception {

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        JWK jwk = JWKBuilder.create().kid(KID).rs256(keyPair.getPublic());
        byte[] jwks = JsonSerialization.writeValueAsBytes(Map.of("keys", List.of(jwk)));

        jwksServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(jwks);
            }
        });
        jwksServer.start();
        jwksUrl = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
    }

    @AfterAll
    static void tearDown() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void acceptsValidToken() {
        assertTrue(verifier().verify(sign(validToken())));
    }

    @Test
    void acceptsTokenExpiredWithinClockSkew() {
        AccessToken token = validToken();
        token.exp((long) Time.currentTime() - 30);
        assertTrue(verifier().verify(sign(token)));
    }

    @Test
    void rejectsExpiredToken() {
        AccessToken token = validToken();
        token.iat((long) Time.currentTime() - 3600);
        token.exp((long) Time.currentTime() - 600);
        assertFalse(verifier().verify(sign(token)));
    }

    @Test
    void rejectsTokenWithoutExp() {
        AccessToken token = validToken();
        token.exp(null);
        assertFalse(verifier().verify(sign(token)));
    }

    @Test
    void rejectsTokenNotYetValid() {
        AccessToken token = validToken();
        token.nbf((long) Time.currentTime() + 600);
        assertFalse(verifier().verify(sign(token)));
    }

    @Test
    void acceptsTokenWithNbfWithinClockSkew() {
        AccessToken token = validToken();
        token.nbf((long) Time.currentTime() + 30);
        assertTrue(verifier().verify(sign(token)));
    }

    @Test
    void rejectsTokenWithoutIssuer() {
        AccessToken token = validToken();
        token.issuer(null);
        assertFalse(verifier().verify(sign(token)));
    }

    @Test
    void rejectsTokenWithoutAudience() {
        AccessToken token = validToken();
        token.audience((String[]) null);
        assertFalse(verifier().verify(sign(token)));
    }

    @Test
    void wildcardSkipsMissingIssuerAndAudience() {
        AccessToken token = validToken();
        token.issuer(null);
        token.audience((String[]) null);
        assertTrue(new ExternalTokenVerifier("*", jwksUrl, "*").verify(sign(token)));
    }

    @Test
    void rejectsTokenSignedWithOtherKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherKeyPair = generator.generateKeyPair();
        String tokenString = new JWSBuilder().kid(KID).jsonContent(validToken()).rsa256(otherKeyPair.getPrivate());
        assertFalse(verifier().verify(tokenString));
    }

    /**
     * Returns a verifier expecting the test issuer and audience
     *
     * @return verifier
     */
    private ExternalTokenVerifier verifier() {
        return new ExternalTokenVerifier(ISSUER, jwksUrl, AUDIENCE);
    }

    /**
     * Returns a token that is valid for five minutes
     *
     * @return token
     */
    private AccessToken validToken() {
        long now = Time.currentTime();
        AccessToken token = new AccessToken();
        token.issuer(ISSUER);
        token.audience(AUDIENCE);
        token.iat(now);
        token.exp(now + 300);
        return token;
    }

    /**
     * Signs the token with the test key
     *
     * @param token token
     * @return signed JWT
     */
    private String sign(AccessToken token) {
        return new JWSBuilder().kid(KID).jsonContent(token).rsa256(keyPair.getPrivate());
    }

}

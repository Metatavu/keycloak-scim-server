package fi.metatavu.keycloak.scim.server.authentication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.jose.jwk.JWKParser;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for JWKS
 */
public class JwksUtils {

    /**
     * Loads all public keys from JWKS URL
     *
     * @param jwksUrl JWKS endpoint URL
     * @return list of public keys
     */
    public static List<JwkKey> getPublicKeysFromJwks(String jwksUrl) throws URISyntaxException, IOException, InterruptedException {
        List<JwkKey> result = new ArrayList<>();

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(jwksUrl))
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch JWKS: HTTP " + response.statusCode());
            }

            ObjectMapper objectMapper = new ObjectMapper();

            Map<String, Object> jwks = objectMapper.readValue(response.body(), new TypeReference<>() { });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

            if (keys == null || keys.isEmpty()) {
                throw new RuntimeException("No keys found in JWKS");
            }

            for (Map<String, Object> jwk : keys) {
                String kid = (String) jwk.get("kid");
                String use = (String) jwk.get("use");

                if (kid == null) continue;

                if (use == null) {
                    use = "sig";
                }

                String jwkJson = objectMapper.writeValueAsString(jwk);
                PublicKey publicKey = JWKParser.create()
                    .parse(jwkJson)
                    .toPublicKey();

                result.add(new JwkKey(publicKey, kid, use));
            }

            return result;
        }
    }

}

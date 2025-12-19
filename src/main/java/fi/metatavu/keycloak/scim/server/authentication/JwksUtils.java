package fi.metatavu.keycloak.scim.server.authentication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.scim.server.ScimContext;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.jose.jwk.JWKParser;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
     * @param jwksUrl     JWKS endpoint URL
     * @param scimContext the context, used to retrieve a Keycloak HTTP client
     * @return list of public keys
     */
    public static List<JwkKey> getPublicKeysFromJwks(String jwksUrl, ScimContext scimContext) throws URISyntaxException, IOException, InterruptedException {
        List<JwkKey> result = new ArrayList<>();

        HttpClientProvider clientProvider = scimContext.getSession().getProvider(HttpClientProvider.class);
        // never close an HttpClient from the Keycloak pool
        CloseableHttpClient httpClient = clientProvider.getHttpClient();

        HttpGet request = new HttpGet(new URI(jwksUrl));
        try (CloseableHttpResponse response = httpClient.execute(request)) {

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("Failed to fetch JWKS: HTTP " + statusCode);
            }

            ObjectMapper objectMapper = new ObjectMapper();

            Map<String, Object> jwks = objectMapper.readValue(response.getEntity().getContent(), new TypeReference<>() { });
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
        }
        return result;
    }
}

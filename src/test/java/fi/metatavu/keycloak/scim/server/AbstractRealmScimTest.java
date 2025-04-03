package fi.metatavu.keycloak.scim.server;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import java.net.URI;

public abstract class AbstractRealmScimTest extends AbstractScimTest {

    /**
     * Returns SCIM URI for the test realm
     *
     * @return SCIM URI
     */
    protected URI getScimUri() {
        return URI.create(getKeycloakContainer().getAuthServerUrl()).resolve(String.format("/realms/%s/scim/v2/", TestConsts.TEST_REALM));
    }

    /**
     * Returns authenticated SCIM client
     *
     * @return authenticated SCIM client
     */
    protected ScimClient getAuthenticatedScimClient() {
        return new ScimClient(getScimUri(), getServiceAccountToken());
    }

    /**
     * Returns service account token
     *
     * @return service account token
     */


    /**
     * Returns service account token
     *
     * @return service account token
     */
    protected String getServiceAccountToken() {
        try (Keycloak keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(getKeycloakContainer().getAuthServerUrl())
                .realm(TestConsts.TEST_REALM)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(TestConsts.TEST_SCIM_CLIENT_ID)
                .clientSecret(TestConsts.TEST_SCIM_CLIENT_SECRET)
                .build()) {

            return keycloakAdmin
                    .tokenManager()
                    .getAccessToken()
                    .getToken();
        }
    }

}

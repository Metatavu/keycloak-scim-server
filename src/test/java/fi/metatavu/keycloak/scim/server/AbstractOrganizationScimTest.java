package fi.metatavu.keycloak.scim.server;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import java.net.URI;

public abstract class AbstractOrganizationScimTest extends AbstractScimTest {

    /**
     * Returns SCIM URI for the test realm
     *
     * @return SCIM URI
     */
    protected URI getScimUri(String organizationId) {
        return URI.create(getKeycloakContainer().getAuthServerUrl()).resolve(String.format("/realms/%s/scim/v2/organizations/%s/", TestConsts.ORGANIZATIONS_REALM, organizationId));
    }

    /**
     * Returns authenticated SCIM client
     *
     * @return authenticated SCIM client
     */
    protected ScimClient getAuthenticatedScimClient(String organizationId) {
        return new ScimClient(getScimUri(organizationId), getExternalServiceAccountToken());
    }

    /**
     * Returns access token for external service account
     *
     * @return access token
     */
    protected String getExternalServiceAccountToken() {
        try (Keycloak keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(getKeycloakContainer().getAuthServerUrl())
                .realm(TestConsts.EXTERNAL_REALM)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(TestConsts.EXTERNAL_CLIENT_ID)
                .clientSecret(TestConsts.EXTERNAL_CLIENT_SECRET)
                .build()) {

            return keycloakAdmin
                    .tokenManager()
                    .getAccessToken()
                    .getToken();
        }
    }

}

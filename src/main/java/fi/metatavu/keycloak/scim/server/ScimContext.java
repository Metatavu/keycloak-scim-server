package fi.metatavu.keycloak.scim.server;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.net.URI;

/**
 * SCIM context
 */
public class ScimContext {

    private final URI baseUri;
    private final KeycloakSession session;
    private final RealmModel realm;

    /**
     * Constructor
     *
     * @param baseUri base URI
     * @param session keycloak session
     * @param realm realm
     */
    ScimContext(URI baseUri, KeycloakSession session, RealmModel realm) {
        this.baseUri = baseUri;
        this.session = session;
        this.realm = realm;
    }

    public URI getBaseUri() {
        return baseUri;
    }

    public KeycloakSession getSession() {
        return session;
    }

    public RealmModel getRealm() {
        return realm;
    }

}

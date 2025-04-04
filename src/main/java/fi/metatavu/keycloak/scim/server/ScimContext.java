package fi.metatavu.keycloak.scim.server;

import fi.metatavu.keycloak.scim.server.config.ScimConfig;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.net.URI;

/**
 * SCIM context
 */
public abstract class ScimContext {

    private final URI serverBaseUri;
    private final KeycloakSession session;
    private final RealmModel realm;
    private final ScimConfig config;

    /**
     * Constructor
     *
     * @param serverBaseUri server base URI
     * @param session keycloak session
     * @param realm realm
     */
    public ScimContext(URI serverBaseUri, KeycloakSession session, RealmModel realm, ScimConfig config) {
        this.serverBaseUri = serverBaseUri;
        this.session = session;
        this.realm = realm;
        this.config = config;
    }

    /**
     * Gets the server base URI
     *
     * @return server base URI
     */
    public URI getServerBaseUri() {
        return serverBaseUri;
    }

    /**
     * Gets the keycloak session
     *
     * @return keycloak session
     */
    public KeycloakSession getSession() {
        return session;
    }

    /**
     * Gets the realm
     *
     * @return realm
     */
    public RealmModel getRealm() {
        return realm;
    }

    /**
     * Gets the SCIM configuration
     *
     * @return SCIM configuration
     */
    public ScimConfig getConfig() {
        return config;
    }
}

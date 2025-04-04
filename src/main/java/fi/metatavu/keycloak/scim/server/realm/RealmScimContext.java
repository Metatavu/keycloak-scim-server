package fi.metatavu.keycloak.scim.server.realm;

import fi.metatavu.keycloak.scim.server.ScimContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.net.URI;

/**
 * SCIM context for realms
 */
public class RealmScimContext extends ScimContext {

    /**
     * Constructor
     *
     * @param serverBaseUri base URI of the SCIM server
     * @param session keycloak session
     * @param realm   realm
     * @param config  SCIM configuration
     */
    RealmScimContext(URI serverBaseUri, KeycloakSession session, RealmModel realm, RealmScimConfig config) {
        super(serverBaseUri, session, realm, config);
    }

}

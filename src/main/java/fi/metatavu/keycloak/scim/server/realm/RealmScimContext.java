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
     * @param baseUri base URI
     * @param session keycloak session
     * @param realm   realm
     */
    RealmScimContext(URI baseUri, KeycloakSession session, RealmModel realm) {
        super(baseUri, session, realm);
    }

}

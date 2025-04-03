package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.ScimContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.RealmModel;

import java.net.URI;

/**
 * SCIM context for organizations
 */
public class OrganizationScimContext extends ScimContext {

    private final OrganizationModel organization;

    /**
     * Constructor
     *
     * @param baseUri base URI
     * @param session keycloak session
     * @param realm realm
     * @param organization organization
     */
    public OrganizationScimContext(URI baseUri, KeycloakSession session, RealmModel realm, OrganizationModel organization, OrganizationScimConfig config) {
        super(baseUri, session, realm, config);
        this.organization = organization;
    }

    /**
     * Gets the organization
     *
     * @return organization
     */
    public OrganizationModel getOrganization() {
        return organization;
    }

}

package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.AbstractController;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.organization.OrganizationProvider;

public class OrganizationController extends AbstractController  {

    public OrganizationModel findOrganizationById(
            KeycloakSession session,
            String organizationId
    ) {
        return getOrganizationProvider(session).getById(organizationId);
    }

    /**
     * Returns the organization provider
     *
     * @param session Keycloak session
     * @return Organization provider
     */
    private OrganizationProvider getOrganizationProvider(KeycloakSession session) {
        KeycloakContext context = session.getContext();
        if (context == null) {
            throw new IllegalStateException("Keycloak context is not set");
        }

        return session.getProvider(OrganizationProvider.class);
    }

}

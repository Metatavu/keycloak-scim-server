package fi.metatavu.keycloak.scim.server.adminEvents;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.util.JsonSerialization;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdminEventController extends AbstractController {

    private static final Logger logger = Logger.getLogger(AdminEventController.class.getName());
    /**
     * Sends an admin event
     *
     * @param scimContext SCIM context
     * @param operationType operation type
     * @param resourceType resource type
     * @param resourcePath resource path
     * @param representation representation
     */
    @SuppressWarnings("SameParameterValue")
    public void sendAdminEvent(
            ScimContext scimContext,
            OperationType operationType,
            ResourceType resourceType,
            String resourcePath,
            Object representation
    ) {
        sendAdminEvent(scimContext, operationType, resourceType, resourcePath, representation, null);
    }

    /**
     * Sends an admin event with additional details
     *
     * @param scimContext SCIM context
     * @param operationType operation type
     * @param resourceType resource type
     * @param resourcePath resource path
     * @param representation representation
     * @param details additional details
     */
    public void sendAdminEvent(
            ScimContext scimContext,
            OperationType operationType,
            ResourceType resourceType,
            String resourcePath,
            Object representation,
            Map<String, String> details
    ) {
        RealmModel realm = scimContext.getRealm();
        KeycloakSession session = scimContext.getSession();

        boolean includeRepresentation = realm.isAdminEventsDetailsEnabled();

        AdminEvent event = new AdminEvent();
        event.setId(UUID.randomUUID().toString());
        event.setRealmId(realm.getId());
        event.setRealmName(realm.getName());
        event.setOperationType(operationType);
        event.setResourceType(resourceType);
        event.setResourcePath(resourcePath);
        event.setTime(System.currentTimeMillis());
        event.setDetails(details);

        AuthDetails authDetails = new AuthDetails();
        authDetails.setRealmId(scimContext.getRealm().getId());
        authDetails.setRealmName(scimContext.getRealm().getName());
        authDetails.setUserId("SCIM_CLIENT");
        event.setAuthDetails(authDetails);

        if (representation != null) {
            try {
                event.setRepresentation(JsonSerialization.writeValueAsString(representation));
            } catch (IOException e) {
                logger.errorf(e, "Failed to serialize representation for admin event: %s %s %s", operationType, resourceType, resourcePath);
                throw new RuntimeException(e);
            }
        }

        List<String> realmListenerIds = realm.getEventsListenersStream().toList();

        if (realm.isAdminEventsEnabled()) {
            EventStoreProvider store = session.getProvider(EventStoreProvider.class);
            if (store != null) {
                store.onEvent(event, includeRepresentation);
            } else {
                logger.warn("Admin events enabled but no EventStoreProvider found — event not persisted");
            }
        }

        session.getKeycloakSessionFactory()
                .getProviderFactoriesStream(EventListenerProvider.class)
                .filter(providerFactory -> realmListenerIds.contains(providerFactory.getId()) || ((EventListenerProviderFactory) providerFactory).isGlobal())
                .map(providerFactory -> providerFactory.create(session))
                .forEach(provider -> {
                    if (provider instanceof EventListenerProvider eventListenerProvider) {
                        logger.debugf("Sending admin event: %s %s %s", operationType, resourceType, resourcePath);
                        eventListenerProvider.onEvent(event, includeRepresentation);
                    }
                });
    }
}
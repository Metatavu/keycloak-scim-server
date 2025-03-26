package fi.metatavu.keycloak.scim.server.filter;

/**
 * Presence SCIM filter
 * <p>
 * This class is responsible for presence SCIM filters
 *
 * @param attribute attribute
 */
public record PresenceFilter(String attribute) implements ScimFilter {
}

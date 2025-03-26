package fi.metatavu.keycloak.scim.server.filter;

/**
 * Comparison SCIM filter
 * <p>
 * This class is responsible for comparison SCIM filters
 *
 * @param attribute attribute
 * @param operator operator
 * @param value value
 */
public record ComparisonFilter(String attribute, Operator operator, String value) implements ScimFilter {
}

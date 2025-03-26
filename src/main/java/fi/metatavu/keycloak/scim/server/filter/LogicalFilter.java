package fi.metatavu.keycloak.scim.server.filter;

/**
 * Logical SCIM filter
 * <p>
 * This class is responsible for logical SCIM filters
 *
 * @param operator operator
 * @param left left filter
 * @param right right filter
 */
public record LogicalFilter(Operator operator, ScimFilter left, ScimFilter right) implements ScimFilter {
}

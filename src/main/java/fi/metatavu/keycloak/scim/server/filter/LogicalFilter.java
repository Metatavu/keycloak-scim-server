package fi.metatavu.keycloak.scim.server.filter;

import fi.metatavu.keycloak.scim.server.attribute.ScimAttributeAccessor;

/**
 * Logical SCIM filter
 * <p>
 * This class is responsible for logical SCIM filters
 *
 * @param operator operator
 * @param left left filter
 * @param right right filter
 */
public class LogicalFilter implements ScimFilter {

    private final Operator operator;
    private final ScimFilter left;
    private final ScimFilter right;

    public LogicalFilter(Operator operator, ScimFilter left, ScimFilter right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean matches(ScimAttributeAccessor accessor) {
        boolean l = left.matches(accessor);
        boolean r = right.matches(accessor);

        return switch (operator) {
            case AND -> l && r;
            case OR -> l || r;
            default -> false;
        };
    }

    public ScimFilter getLeft() { return left; }
    public ScimFilter getRight() { return right; }
    public Operator getOperator() { return operator; }
}
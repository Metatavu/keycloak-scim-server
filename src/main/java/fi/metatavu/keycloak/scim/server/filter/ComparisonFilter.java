package fi.metatavu.keycloak.scim.server.filter;

import fi.metatavu.keycloak.scim.server.attribute.ScimAttributeAccessor;

/**
 * Comparison SCIM filter
 * <p>
 * This class is responsible for comparison SCIM filters
 *
 * @param attribute attribute
 * @param operator operator
 * @param value value
 */
public class ComparisonFilter implements ScimFilter {

    private final String attr;
    private final Operator operator;
    private final String value;

    public ComparisonFilter(String attr, Operator operator, String value) {
        this.attr = attr;
        this.operator = operator;
        this.value = value;
    }

    @Override
    public boolean matches(ScimAttributeAccessor accessor) {
        Object actual = accessor.read(attr);
        if (actual == null) {
            return false;
        }

        String actualString = String.valueOf(actual);

        return switch (operator) {
            case EQ -> actualString.equalsIgnoreCase(value);
            case CO -> actualString.toLowerCase().contains(value.toLowerCase());
            case SW -> actualString.toLowerCase().startsWith(value.toLowerCase());
            case EW -> actualString.toLowerCase().endsWith(value.toLowerCase());
            default -> false;
        };
    }

    public String getAttr() { return attr; }
    public String getValue() { return value; }
    public Operator getOperator() { return operator; }
}
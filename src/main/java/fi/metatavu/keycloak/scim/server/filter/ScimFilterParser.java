package fi.metatavu.keycloak.scim.server.filter;

import java.util.regex.*;

/**
 * SCIM filter parser
 */
public class ScimFilterParser {

    private static final Pattern EQ_PATTERN = Pattern.compile(
            "(\\w+(\\.\\w+)*)\\s+eq\\s+(\"[^\"]+\"|true|false|\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PR_PATTERN = Pattern.compile(
            "(\\w+(\\.\\w+)*)\\s+pr",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LOGICAL_PATTERN = Pattern.compile(
            "(.+)\\s+(and|or)\\s+(.+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Parses an SCIM filter
     *
     * @param filter filter
     * @return parsed filter
     */
    public ScimFilter parse(String filter) {
        filter = filter.trim();

        Matcher logical = LOGICAL_PATTERN.matcher(filter);
        if (logical.matches()) {
            ScimFilter left = parse(logical.group(1).trim());
            ScimFilter right = parse(logical.group(3).trim());
            String op = logical.group(2).toLowerCase();
            return new LogicalFilter(
                    op.equals("and") ? ScimFilter.Operator.AND : ScimFilter.Operator.OR,
                    left,
                    right
            );
        }

        Matcher pr = PR_PATTERN.matcher(filter);
        if (pr.matches()) {
            return new PresenceFilter(pr.group(1));
        }

        Matcher eq = EQ_PATTERN.matcher(filter);
        if (eq.matches()) {
            String attr = eq.group(1).trim();
            String rawValue = eq.group(3).trim().stripLeading();

            if (rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
                rawValue = rawValue.substring(1, rawValue.length() - 1);
            }

            return new ComparisonFilter(attr, ScimFilter.Operator.EQ, rawValue);
        }

        throw new UnsupportedFilter(filter);
    }

}

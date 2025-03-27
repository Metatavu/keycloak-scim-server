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

    private static final Pattern CO_PATTERN = Pattern.compile(
            "(\\w+(\\.\\w+)*)\\s+co\\s+(\"[^\"]+\")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SW_PATTERN = Pattern.compile(
            "(\\w+(\\.\\w+)*)\\s+sw\\s+(\"[^\"]+\")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EW_PATTERN = Pattern.compile(
            "(\\w+(\\.\\w+)*)\\s+ew\\s+(\"[^\"]+\")",
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
            return parseComparison(eq, ScimFilter.Operator.EQ);
        }

        Matcher co = CO_PATTERN.matcher(filter);
        if (co.matches()) {
            return parseComparison(co, ScimFilter.Operator.CO);
        }

        Matcher sw = SW_PATTERN.matcher(filter);
        if (sw.matches()) {
            return parseComparison(sw, ScimFilter.Operator.SW);
        }

        Matcher ew = EW_PATTERN.matcher(filter);
        if (ew.matches()) {
            return parseComparison(ew, ScimFilter.Operator.EW);
        }

        throw new UnsupportedFilter(filter);
    }

    private ComparisonFilter parseComparison(Matcher matcher, ScimFilter.Operator operator) {
        String attr = matcher.group(1).trim();
        String rawValue = matcher.group(3).trim();
        if (rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
            rawValue = rawValue.substring(1, rawValue.length() - 1);
        }
        return new ComparisonFilter(attr, operator, rawValue);
    }
}
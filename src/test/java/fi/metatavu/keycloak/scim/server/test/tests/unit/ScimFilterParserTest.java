package fi.metatavu.keycloak.scim.server.test.tests.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import fi.metatavu.keycloak.scim.server.filter.ComparisonFilter;
import fi.metatavu.keycloak.scim.server.filter.LogicalFilter;
import fi.metatavu.keycloak.scim.server.filter.PresenceFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilterParser;
import fi.metatavu.keycloak.scim.server.filter.UnsupportedFilter;
import fi.metatavu.keycloak.scim.server.filter.ValuePathFilter;

/**
 * Tests for {@link ScimFilterParser}
 */
public class ScimFilterParserTest {

    private final ScimFilterParser parser = new ScimFilterParser();

    @Test
    public void testSimpleEqFilter() {
        ScimFilter result = parser.parse("userName eq \"alice@example.com\"");
        assertInstanceOf(ComparisonFilter.class, result);
        ComparisonFilter filter = (ComparisonFilter) result;
        assertEquals("userName", filter.getAttr());
        assertEquals(ScimFilter.Operator.EQ, filter.getOperator());
        assertEquals("alice@example.com", filter.getValue());
    }
    @Test
    public void testValuePathEqFilter() {
        ScimFilter result = parser.parse("emails[value eq \"alice@example.com\"]");
        assertInstanceOf(ValuePathFilter.class, result);
        ValuePathFilter filter = (ValuePathFilter) result;
        assertEquals("emails", filter.getAttrPath());
        assertInstanceOf(ComparisonFilter.class, filter.getInnerFilter());
        ComparisonFilter filter1 = (ComparisonFilter) filter.getInnerFilter();
        assertEquals(ScimFilter.Operator.EQ, filter1.getOperator());
        assertEquals("alice@example.com", filter1.getValue());
    }

    @Test
    public void testPresenceFilter() {
        ScimFilter result = parser.parse("userName pr");
        assertInstanceOf(PresenceFilter.class, result);
        PresenceFilter filter = (PresenceFilter) result;
        assertEquals("userName", filter.getAttr());
    }

    @Test
    public void testAndFilter() {
        ScimFilter result = parser.parse("userName eq \"bob@example.com\" and active eq true");
        assertInstanceOf(LogicalFilter.class, result);

        LogicalFilter logical = (LogicalFilter) result;
        assertEquals(ScimFilter.Operator.AND, logical.getOperator());

        assertInstanceOf(ComparisonFilter.class, logical.getLeft());
        assertInstanceOf(ComparisonFilter.class, logical.getRight());

        ComparisonFilter left = (ComparisonFilter) logical.getLeft();
        assertEquals("userName", left.getAttr());
        assertEquals("bob@example.com", left.getValue());

        ComparisonFilter right = (ComparisonFilter) logical.getRight();
        assertEquals("active", right.getAttr());
        assertEquals("true", right.getValue());
    }

    @Test
    public void testOrFilter() {
        ScimFilter result = parser.parse("active eq false or userName eq \"test@example.com\"");
        assertInstanceOf(LogicalFilter.class, result);

        LogicalFilter logical = (LogicalFilter) result;
        assertEquals(ScimFilter.Operator.OR, logical.getOperator());

        ComparisonFilter left = (ComparisonFilter) logical.getLeft();
        ComparisonFilter right = (ComparisonFilter) logical.getRight();

        assertEquals("active", left.getAttr());
        assertEquals("false", left.getValue());

        assertEquals("userName", right.getAttr());
        assertEquals("test@example.com", right.getValue());
    }

    @Test
    public void testTrimAndCaseInsensitive() {
        ScimFilter result = parser.parse("  userName   EQ  \"test\"  ");
        assertInstanceOf(ComparisonFilter.class, result);
        ComparisonFilter filter = (ComparisonFilter) result;
        assertEquals("userName", filter.getAttr());
        assertEquals("test", filter.getValue());
    }

    @Test
    public void testContainsFilter() {
        ScimFilter result = parser.parse("name.familyName co \"Stark\"");
        assertInstanceOf(ComparisonFilter.class, result);
        ComparisonFilter filter = (ComparisonFilter) result;
        assertEquals("name.familyName", filter.getAttr());
        assertEquals(ScimFilter.Operator.CO, filter.getOperator());
        assertEquals("Stark", filter.getValue());
    }

    @Test
    public void testStartsWithFilter() {
        ScimFilter result = parser.parse("userName sw \"test\"");
        assertInstanceOf(ComparisonFilter.class, result);
        ComparisonFilter filter = (ComparisonFilter) result;
        assertEquals("userName", filter.getAttr());
        assertEquals(ScimFilter.Operator.SW, filter.getOperator());
        assertEquals("test", filter.getValue());
    }

    @Test
    public void testEndsWithFilter() {
        ScimFilter result = parser.parse("email ew \"@example.com\"");
        assertInstanceOf(ComparisonFilter.class, result);
        ComparisonFilter filter = (ComparisonFilter) result;
        assertEquals("email", filter.getAttr());
        assertEquals(ScimFilter.Operator.EW, filter.getOperator());
        assertEquals("@example.com", filter.getValue());
    }

    @Test
    public void testInvalidFilterThrows() {
        assertThrows(UnsupportedFilter.class, () -> parser.parse("userName foo \"x\""));
        assertThrows(UnsupportedFilter.class, () -> parser.parse("something = wrong"));
    }
}
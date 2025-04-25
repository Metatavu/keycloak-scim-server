package fi.metatavu.keycloak.scim.server.test.tests.unit;

import fi.metatavu.keycloak.scim.server.filter.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals("userName", filter.attribute());
        assertEquals(ScimFilter.Operator.EQ, filter.operator());
        assertEquals("alice@example.com", filter.value());
    }

    @Test
    public void testPresenceFilter() {
        ScimFilter result = parser.parse("userName pr");
        assertInstanceOf(PresenceFilter.class, result);
        PresenceFilter filter = (PresenceFilter) result;
        assertEquals("userName", filter.attribute());
    }

    @Test
    public void testAndFilter() {
        ScimFilter result = parser.parse("userName eq \"bob@example.com\" and active eq true");
        assertInstanceOf(LogicalFilter.class, result);

        LogicalFilter logical = (LogicalFilter) result;
        assertEquals(ScimFilter.Operator.AND, logical.operator());

        assertInstanceOf(ComparisonFilter.class, logical.left());
        assertInstanceOf(ComparisonFilter.class, logical.right());

        ComparisonFilter left = (ComparisonFilter) logical.left();
        assertEquals("userName", left.attribute());
        assertEquals("bob@example.com", left.value());

        ComparisonFilter right = (ComparisonFilter) logical.right();
        assertEquals("active", right.attribute());
        assertEquals("true", right.value());
    }

    @Test
    public void testOrFilter() {
        ScimFilter result = parser.parse("active eq false or userName eq \"test@example.com\"");
        assertInstanceOf(LogicalFilter.class, result);

        LogicalFilter logical = (LogicalFilter) result;
        assertEquals(ScimFilter.Operator.OR, logical.operator());

        ComparisonFilter left = (ComparisonFilter) logical.left();
        ComparisonFilter right = (ComparisonFilter) logical.right();

        assertEquals("active", left.attribute());
        assertEquals("false", left.value());

        assertEquals("userName", right.attribute());
        assertEquals("test@example.com", right.value());
    }

    @Test
    public void testTrimAndCaseInsensitive() {
        ScimFilter result = parser.parse("  userName   EQ  \"test\"  ");
        assertInstanceOf(ComparisonFilter.class, result);
        ComparisonFilter filter = (ComparisonFilter) result;
        assertEquals("userName", filter.attribute());
        assertEquals("test", filter.value());
    }

    @Test
    public void testContainsFilter() {
        ScimFilter result = parser.parse("name.familyName co \"Stark\"");
        assertInstanceOf(ComparisonFilter.class, result);
        ComparisonFilter filter = (ComparisonFilter) result;
        assertEquals("name.familyName", filter.attribute());
        assertEquals(ScimFilter.Operator.CO, filter.operator());
        assertEquals("Stark", filter.value());
    }

    @Test
    public void testStartsWithFilter() {
        ScimFilter result = parser.parse("userName sw \"test\"");
        assertInstanceOf(ComparisonFilter.class, result);
        ComparisonFilter filter = (ComparisonFilter) result;
        assertEquals("userName", filter.attribute());
        assertEquals(ScimFilter.Operator.SW, filter.operator());
        assertEquals("test", filter.value());
    }

    @Test
    public void testEndsWithFilter() {
        ScimFilter result = parser.parse("email ew \"@example.com\"");
        assertInstanceOf(ComparisonFilter.class, result);
        ComparisonFilter filter = (ComparisonFilter) result;
        assertEquals("email", filter.attribute());
        assertEquals(ScimFilter.Operator.EW, filter.operator());
        assertEquals("@example.com", filter.value());
    }

    @Test
    public void testInvalidFilterThrows() {
        assertThrows(UnsupportedFilter.class, () -> parser.parse("userName foo \"x\""));
        assertThrows(UnsupportedFilter.class, () -> parser.parse("something = wrong"));
    }
}
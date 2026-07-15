package fi.metatavu.keycloak.scim.server.test.tests.unit;

import fi.metatavu.keycloak.scim.server.ScimPagination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScimPaginationTest {

    @Test
    void normalizeStartIndexDefaultsToOne() {
        assertEquals(1, ScimPagination.normalizeStartIndex(null));
        assertEquals(1, ScimPagination.normalizeStartIndex(0));
        assertEquals(1, ScimPagination.normalizeStartIndex(-5));
    }

    @Test
    void normalizeStartIndexPreservesValidValues() {
        assertEquals(1, ScimPagination.normalizeStartIndex(1));
        assertEquals(3, ScimPagination.normalizeStartIndex(3));
        assertEquals(100, ScimPagination.normalizeStartIndex(100));
    }

    @Test
    void toZeroBasedOffset() {
        assertEquals(0, ScimPagination.toZeroBasedOffset(null));
        assertEquals(0, ScimPagination.toZeroBasedOffset(1));
        assertEquals(2, ScimPagination.toZeroBasedOffset(3));
        assertEquals(0, ScimPagination.toZeroBasedOffset(0));
    }
}

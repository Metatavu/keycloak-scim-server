package fi.metatavu.keycloak.scim.server.test.tests.unit;

import fi.metatavu.keycloak.scim.server.ScimPagination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ScimPagination}
 */
class ScimPaginationTest {

    @Test
    void firstResultIsZeroBasedOffsetOfOneBasedStartIndex() {
        assertEquals(0, ScimPagination.firstResult(1));
        assertEquals(2, ScimPagination.firstResult(3));
        assertEquals(199, ScimPagination.firstResult(200));
    }

    @Test
    void firstResultTreatsStartIndexBelowOneAsOne() {
        assertEquals(0, ScimPagination.firstResult(null));
        assertEquals(0, ScimPagination.firstResult(0));
        assertEquals(0, ScimPagination.firstResult(-5));
    }

    @Test
    void maxResultsKeepsCountsUpToTheAdvertisedMaximum() {
        assertEquals(0, ScimPagination.maxResults(0));
        assertEquals(10, ScimPagination.maxResults(10));
        assertEquals(ScimPagination.MAX_RESULTS, ScimPagination.maxResults(ScimPagination.MAX_RESULTS));
    }

    @Test
    void maxResultsCapsCountAboveTheAdvertisedMaximum() {
        assertEquals(ScimPagination.MAX_RESULTS, ScimPagination.maxResults(ScimPagination.MAX_RESULTS + 1));
        assertEquals(ScimPagination.MAX_RESULTS, ScimPagination.maxResults(Integer.MAX_VALUE));
    }

    @Test
    void maxResultsTreatsNegativeCountAsZero() {
        assertEquals(0, ScimPagination.maxResults(-1));
    }

    @Test
    void maxResultsDefaultsWhenCountIsMissing() {
        assertEquals(100, ScimPagination.maxResults(null));
    }

    @Test
    void startIndexIsOneBasedPositionOfOffset() {
        assertEquals(1, ScimPagination.startIndex(0));
        assertEquals(101, ScimPagination.startIndex(100));
    }
}

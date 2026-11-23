package fi.metatavu.keycloak.scim.server;

/**
 * Translates SCIM list paging parameters (RFC 7644 §3.4.2.4) into the
 * zero-based offset and bounded page size the controllers page with.
 *
 * SCIM's {@code startIndex} is 1-based: a client that pages from
 * {@code startIndex=1} expects the first resource on its first page. Treating
 * it as an offset skipped the first resource of every full listing. Values
 * below 1 are interpreted as 1, as the RFC requires.
 *
 * A {@code count} above {@link #MAX_RESULTS} is capped to it, the maximum the
 * server advertises in its ServiceProviderConfig, and a negative one is
 * interpreted as 0.
 */
public final class ScimPagination {

    /**
     * Largest page the server returns, advertised as {@code filter.maxResults}
     */
    public static final int MAX_RESULTS = 200;

    /**
     * Page size when the client sends no {@code count}
     */
    public static final String DEFAULT_COUNT = "100";

    private ScimPagination() {
        // utility class
    }

    /**
     * Returns the zero-based offset of the first resource on the requested page
     *
     * @param startIndex 1-based SCIM start index, may be null
     * @return zero-based offset
     */
    public static int firstResult(Integer startIndex) {
        if (startIndex == null || startIndex < 1) {
            return 0;
        }

        return startIndex - 1;
    }

    /**
     * Returns the number of resources to return on the requested page
     *
     * @param count requested SCIM count, may be null
     * @return page size between 0 and {@link #MAX_RESULTS}
     */
    public static int maxResults(Integer count) {
        if (count == null) {
            return Integer.parseInt(DEFAULT_COUNT);
        }

        return Math.clamp(count, 0, MAX_RESULTS);
    }

    /**
     * Returns the 1-based SCIM start index of a page that begins at the given offset
     *
     * @param firstResult zero-based offset
     * @return 1-based SCIM start index
     */
    public static int startIndex(int firstResult) {
        return firstResult + 1;
    }
}

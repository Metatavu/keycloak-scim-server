package fi.metatavu.keycloak.scim.server;

/**
 * SCIM list pagination helpers (RFC 7644 §3.4.2.4).
 */
public final class ScimPagination {

    private ScimPagination() {
    }

    /**
     * Normalizes the SCIM {@code startIndex} query parameter.
     * Values less than 1 are interpreted as 1 per RFC 7644.
     *
     * @param startIndex requested start index, may be null
     * @return 1-based start index for the list response
     */
    public static int normalizeStartIndex(Integer startIndex) {
        if (startIndex == null || startIndex < 1) {
            return 1;
        }
        return startIndex;
    }

    /**
     * Converts a 1-based SCIM {@code startIndex} to a 0-based offset for internal paging.
     *
     * @param startIndex requested start index, may be null
     * @return zero-based offset
     */
    public static int toZeroBasedOffset(Integer startIndex) {
        return normalizeStartIndex(startIndex) - 1;
    }
}

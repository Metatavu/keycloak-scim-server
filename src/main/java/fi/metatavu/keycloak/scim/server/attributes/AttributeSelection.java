package fi.metatavu.keycloak.scim.server.attributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves which attributes a response is allowed to carry, per RFC 7644 §3.9.
 *
 * <p>A response normally carries the minimum attribute set (those whose
 * "returned" characteristic is "always", i.e. id/schemas/meta) plus the default
 * attribute set ("returned" = "default"). A client narrows that with one of two
 * mutually exclusive query parameters:
 *
 * <ul>
 *   <li>{@code attributes} — overrides the default set entirely: only the
 *       minimum set plus the named attributes are returned.</li>
 *   <li>{@code excludedAttributes} — the default set minus the named
 *       attributes, minimum set still returned.</li>
 * </ul>
 *
 * <p>This matters for Groups specifically. The core Group schema (RFC 7643
 * §8.7.1) marks {@code members} as "returned": "default", so a plain GET must
 * serialise the whole membership. Microsoft Entra ID issues
 * {@code ?excludedAttributes=members} on both group reads precisely to avoid
 * that cost, and its SCIM compatibility requirements list support for that
 * parameter as mandatory. Honouring it turns an O(group size) response into a
 * constant one.
 *
 * <p>Names are matched case-insensitively and may be URN-qualified or use
 * dotted sub-attribute notation (RFC 7644 §3.10); both are normalised to the
 * top-level attribute name, which is the granularity this server filters at.
 */
public final class AttributeSelection {

    /** Selection applied when the client passes neither parameter. */
    public static final AttributeSelection DEFAULT = new AttributeSelection(null, Collections.emptySet());

    private final Set<String> requested;
    private final Set<String> excluded;

    private AttributeSelection(Set<String> requested, Set<String> excluded) {
        this.requested = requested;
        this.excluded = excluded;
    }

    /**
     * Parses the "attributes" and "excludedAttributes" query parameters.
     *
     * @param attributes comma-separated attribute names, or null
     * @param excludedAttributes comma-separated attribute names, or null
     * @return parsed selection
     * @throws ConflictingAttributeSelection if both parameters carry values; RFC 7644
     *         §3.9 defines them as mutually exclusive
     */
    public static AttributeSelection parse(
            String attributes,
            String excludedAttributes
    ) throws ConflictingAttributeSelection {
        Set<String> requested = split(attributes);
        Set<String> excluded = split(excludedAttributes);

        if (!requested.isEmpty() && !excluded.isEmpty()) {
            throw new ConflictingAttributeSelection();
        }

        if (requested.isEmpty() && excluded.isEmpty()) {
            return DEFAULT;
        }

        return new AttributeSelection(requested.isEmpty() ? null : requested, excluded);
    }

    /**
     * Whether the client explicitly named the attributes it wants.
     *
     * <p>RFC 7644 §3.5.2 makes this decisive for PATCH: a server that would
     * otherwise answer 204 No Content MUST return 200 OK with a body when
     * "attributes" is specified.
     *
     * @return true if the "attributes" parameter was supplied
     */
    public boolean isExplicit() {
        return requested != null;
    }

    /**
     * Whether an attribute whose "returned" characteristic is "default" should
     * appear in the response.
     *
     * <p>Attributes in the minimum set ("returned" = "always") are not subject
     * to this check and are always serialised.
     *
     * @param attributeName top-level attribute name, e.g. "members"
     * @return true if the attribute should be included
     */
    public boolean includes(String attributeName) {
        String normalized = normalize(attributeName);

        if (requested != null) {
            return requested.contains(normalized);
        }

        return !excluded.contains(normalized);
    }

    /**
     * Splits a comma-separated parameter value into normalised attribute names.
     *
     * @param value raw query parameter value
     * @return normalised names, empty if the value is null or blank
     */
    private static Set<String> split(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(value.split(","))
                .map(AttributeSelection::normalize)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Reduces an attribute reference to a lower-cased top-level attribute name.
     *
     * <p>Strips any schema URN prefix and any sub-attribute suffix, so
     * "urn:ietf:params:scim:schemas:core:2.0:Group:members.value" and "members"
     * both normalise to "members". RFC 7644 §3.10 makes every facet of an
     * attribute name case insensitive.
     *
     * @param value raw attribute reference
     * @return normalised name
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String name = value.strip();

        int urnSeparator = name.lastIndexOf(':');
        if (urnSeparator != -1) {
            name = name.substring(urnSeparator + 1);
        }

        int subAttribute = name.indexOf('.');
        if (subAttribute != -1) {
            name = name.substring(0, subAttribute);
        }

        return name.toLowerCase(Locale.ROOT);
    }
}

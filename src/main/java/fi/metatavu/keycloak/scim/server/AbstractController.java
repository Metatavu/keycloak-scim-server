package fi.metatavu.keycloak.scim.server;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * Abstract controller
 */
public class AbstractController {

    private final Date createdAt = getDate(2025, 3, 26);
    private final Date lastModifiedAt = getDate(2025, 3, 27);

    /**
     * Returns meta object
     *
     * @param scimContext SCIM context
     * @param resourceType resource type
     * @param resourcePath resource path
     * @return meta object
     */
    protected fi.metatavu.keycloak.scim.server.model.Meta getMeta(
        ScimContext scimContext,
        String resourceType,
        String resourcePath
    ) {
        fi.metatavu.keycloak.scim.server.model.Meta result = new fi.metatavu.keycloak.scim.server.model.Meta();
        result.setCreated(createdAt);
        result.setLastModified(lastModifiedAt);
        result.setResourceType(resourceType);
        result.setLocation(scimContext.getServerBaseUri().resolve(resourcePath));
        return result;
    }

    /**
     * Whether the given SCIM attribute path refers to a read-only or
     * structural core attribute that PATCH must ignore per RFC 7644 §3.5.2
     * (and the SCIM core schema, RFC 7643 §3.1).
     *
     * Concretely: "id" (server-assigned resource identifier), "meta"
     * (server-assigned metadata), and "schemas" (structural). Servers MUST
     * not error on these in PATCH payloads; clients (notably Okta on Group
     * Push and user provisioning) echo them back when a PATCH value is
     * constructed from a prior GET. Resource controllers consult this
     * before resolving an attribute and silently skip the operation when
     * it matches.
     *
     * Note: "externalId" is intentionally NOT in this list. Per RFC 7643
     * §3.1 externalId is an OPTIONAL, client-settable attribute and a
     * legitimate target of PATCH.
     *
     * @param attrPath attribute path from a PatchOp (path-less or path-based)
     * @return true if the attribute is read-only / structural and must be ignored
     */
    protected static boolean isReadOnlyOrStructural(String attrPath) {
        if (attrPath == null) {
            return false;
        }
        return switch (attrPath) {
            case "id", "meta", "schemas" -> true;
            default -> false;
        };
    }

    /**
     * Returns date based on year, month and date
     *
     * @param year year
     * @param month month
     * @param date date
     * @return date
     */
    @SuppressWarnings("SameParameterValue")
    private Date getDate(int year, int month, int date) {
        return Date.from(OffsetDateTime.of(year, month, date, 0, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }

}

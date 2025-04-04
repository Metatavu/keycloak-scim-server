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

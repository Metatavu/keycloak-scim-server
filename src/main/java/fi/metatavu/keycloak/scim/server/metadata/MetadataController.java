package fi.metatavu.keycloak.scim.server.metadata;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import fi.metatavu.keycloak.scim.server.model.ResourceType;
import fi.metatavu.keycloak.scim.server.model.ResourceTypeMeta;
import org.keycloak.models.RealmModel;

/**
 * Controller for metadata
 */
public class MetadataController {

    /**
     * Lists resource types supported by the SCIM server
     *
     * @param baseUri base URI
     * @param realm realm
     * @return resource types
     */
    public List<ResourceType> listResourceTypes(
            URI baseUri,
            RealmModel realm
    ) {
        ResourceType usersResourceType = new ResourceType();
        usersResourceType.setEndpoint("/Users");
        usersResourceType.setName("User");
        usersResourceType.setDescription("User Account");
        usersResourceType.setSchema("urn:ietf:params:scim:schemas:core:2.0:User");
        usersResourceType.setId("User");
        usersResourceType.setSchemas(Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:ResourceType"));
        usersResourceType.setMeta(new ResourceTypeMeta());
        usersResourceType.getMeta().setResourceType("ResourceType");
        usersResourceType.getMeta().setLocation(baseUri.resolve(String.format("/realms/%s/scim/v2/ResourceTypes/User", realm.getName())));
        usersResourceType.setSchemaExtensions(Collections.emptyList());

        return Collections.singletonList(usersResourceType);
    }

}

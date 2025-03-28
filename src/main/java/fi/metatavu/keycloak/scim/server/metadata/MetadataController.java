package fi.metatavu.keycloak.scim.server.metadata;

import java.net.URI;
import java.util.*;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.consts.UserAttribute;
import fi.metatavu.keycloak.scim.server.model.ResourceType;
import fi.metatavu.keycloak.scim.server.model.SchemaListResponse;
import fi.metatavu.keycloak.scim.server.model.SchemaListItem;
import fi.metatavu.keycloak.scim.server.model.ServiceProviderConfig;
import fi.metatavu.keycloak.scim.server.model.ServiceFeatureSupport;
import fi.metatavu.keycloak.scim.server.model.ServiceProviderConfigBulk;
import fi.metatavu.keycloak.scim.server.model.ServiceProviderConfigFilter;
import fi.metatavu.keycloak.scim.server.model.AuthenticationScheme;
import fi.metatavu.keycloak.scim.server.model.ResourceTypeListResponse;
import fi.metatavu.keycloak.scim.server.model.SchemaAttribute;

/**
 * Controller for metadata
 */
public class MetadataController extends AbstractController {

    /**
     * Lists resource types supported by the SCIM server
     *
     * @param scimContext SCIM context
     * @return resource types
     */
    public ResourceTypeListResponse getResourceTypeList(
        ScimContext scimContext
    ) {
        ResourceTypeListResponse result = new ResourceTypeListResponse();

        List<ResourceType> resourceTypes = getResourceTypes(scimContext);
        result.setSchemas(Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        result.setResources(resourceTypes);
        result.setItemsPerPage(resourceTypes.size());
        result.setTotalResults(resourceTypes.size());
        result.setStartIndex(1);

        return result;
    }

    /**
     * Returns resource type
     *
     * @param scimContext SCIM context
     * @param resourceTypeId resource type id
     * @return resource type
     */
    public ResourceType getResourceType(
        ScimContext scimContext,
        String resourceTypeId
    ) {
        return getResourceTypes(scimContext).stream()
            .filter(resourceType -> resourceType.getId().equals(resourceTypeId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns service provider config
     *
     * @param scimContext SCIM context
     * @return service provider config
     */
    public ServiceProviderConfig getServiceProviderConfig(
        ScimContext scimContext
    ) {
        ServiceProviderConfig config = new ServiceProviderConfig();

        config.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
        config.setDocumentationUri(URI.create("http://example.com/help/scim.html"));

        ServiceFeatureSupport patch = new ServiceFeatureSupport();
        patch.setSupported(false);
        config.setPatch(patch);

        ServiceProviderConfigBulk bulk = new ServiceProviderConfigBulk();
        bulk.setSupported(false);
        bulk.setMaxOperations(1000);
        bulk.setMaxPayloadSize(1048576);
        config.setBulk(bulk);

        ServiceProviderConfigFilter filter = new ServiceProviderConfigFilter();
        filter.setSupported(true);
        filter.setMaxResults(200);
        config.setFilter(filter);

        ServiceFeatureSupport changePassword = new ServiceFeatureSupport();
        changePassword.setSupported(false);
        config.setChangePassword(changePassword);

        ServiceFeatureSupport sort = new ServiceFeatureSupport();
        sort.setSupported(false);
        config.setSort(sort);

        ServiceFeatureSupport etag = new ServiceFeatureSupport();
        etag.setSupported(false);
        config.setEtag(etag);

        AuthenticationScheme auth = new AuthenticationScheme();
        auth.setName("OAuth Bearer Token");
        auth.setDescription("Authentication scheme using the OAuth Bearer Token Standard");
        auth.setSpecUri(URI.create("http://www.rfc-editor.org/info/rfc6750"));
        auth.setDocumentationUri(URI.create("http://example.com/help/oauth.html"));
        auth.setType("oauthbearertoken");
        auth.setPrimary(true);
        config.setAuthenticationSchemes(List.of(auth));
        config.setMeta(getMeta(scimContext, "ServiceProviderConfig", "ServiceProviderConfig"));

        return config;
    }

    /**
     * List schemas
     *
     * @param scimContext SCIM context
     * @return schemas
     */
    public SchemaListResponse listSchemas(
        ScimContext scimContext
    ) {
        SchemaListResponse result = new SchemaListResponse();
        List<SchemaListItem> schemas = Arrays.asList(
            new SchemaListItem()
                .id("urn:ietf:params:scim:schemas:core:2.0:User")
                .name("User")
                .description("SCIM core resource for representing users")
                .meta(getMeta(scimContext, "User", "Schemas/urn:ietf:params:scim:schemas:core:2.0:User"))
                .attributes(Arrays.stream(UserAttribute.values()).map(this::getSchemaAttribute).toList())
                .schemas(Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:Schema")),
            new SchemaListItem()
                .id("urn:ietf:params:scim:schemas:core:2.0:Group")
                .name("Group")
                .description("SCIM core resource for representing groups")
                .meta(getMeta(scimContext, "Group", "Schemas/urn:ietf:params:scim:schemas:core:2.0:Group"))
                .attributes(Collections.emptyList())
                .schemas(Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:Schema"))
        );

        result.setSchemas(Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        result.setResources(schemas);
        result.setTotalResults(schemas.size());
        result.setItemsPerPage(schemas.size());
        result.setStartIndex(1);

        return result;
    }

    /**
     * Returns resource types
     *
     * @param scimContext SCIM context
     * @return resource types
     */
    private List<ResourceType> getResourceTypes(ScimContext scimContext) {
        return Arrays.asList(
            new ResourceType()
                .endpoint("/Users")
                .name("User")
                .description("User Account")
                .schema("urn:ietf:params:scim:schemas:core:2.0:User")
                .id("User")
                .schemas(Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:ResourceType"))
                .meta(getMeta(scimContext, "User", "ResourceTypes/User"))
                .schemaExtensions(Collections.emptyList()),
            new ResourceType()
                .endpoint("/Groups")
                .name("Group")
                .description("Group")
                .schema("urn:ietf:params:scim:schemas:core:2.0:Group")
                .id("Group")
                .schemas(Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:ResourceType"))
                .meta(getMeta(scimContext, "Group", "ResourceTypes/Group"))
                .schemaExtensions(Collections.emptyList())
        );
    }

    /**
     * Returns schema
     *
     * @param scimContext SCIM context
     * @param id schema id
     * @return schema
     */
    public SchemaListItem getSchema(ScimContext scimContext, String id) {
        return listSchemas(scimContext).getResources().stream()
            .filter(schema -> schema.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns schema attribute
     *
     * @param userAttribute user attribute
     * @return schema attribute
     */
    private SchemaAttribute getSchemaAttribute(
            UserAttribute userAttribute
    ) {
        SchemaAttribute result = new SchemaAttribute();
        result.setName(userAttribute.getName());
        result.setDescription(userAttribute.getDescription());
        result.setType(userAttribute.getType());
        result.setMultiValued(false);
        result.setRequired(false);
        result.setCaseExact(false);
        result.setMutability(userAttribute.getMutability());
        result.setReturned(SchemaAttribute.ReturnedEnum.DEFAULT);
        result.setReferenceTypes(null);
        result.setSubAttributes(Collections.emptyList());
        result.setUniqueness(userAttribute.getUniqueness());

        return result;
    }
}

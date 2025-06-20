package fi.metatavu.keycloak.scim.server.metadata;

import java.net.URI;
import java.util.*;

import fi.metatavu.keycloak.scim.server.AbstractController;
import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.config.ScimConfig;
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
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.keycloak.userprofile.UserProfileProvider;

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
        patch.setSupported(true);
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
        List<UserAttribute<?>> userAttributes = getUserAttributeMappingList(scimContext);

        List<SchemaListItem> schemas = Arrays.asList(
            new SchemaListItem()
                .id("urn:ietf:params:scim:schemas:core:2.0:User")
                .name("User")
                .description("SCIM core resource for representing users")
                .meta(getMeta(scimContext, "User", "Schemas/urn:ietf:params:scim:schemas:core:2.0:User"))
                .attributes(userAttributes.stream().map(this::getUserSchemaAttribute).toList())
                .schemas(Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:Schema")),
            new SchemaListItem()
                .id("urn:ietf:params:scim:schemas:core:2.0:Group")
                .name("Group")
                .description("SCIM core resource for representing groups")
                .meta(getMeta(scimContext, "Group", "Schemas/urn:ietf:params:scim:schemas:core:2.0:Group"))
                .attributes(getGroupSchemaAttributes())
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
     * Returns user attributes
     *
     * @param scimContext SCIM context
     * @return user attributes
     */
    public UserAttributes getUserAttributes(ScimContext scimContext) {
        return new UserAttributes(getUserAttributeMappingList(scimContext));
    }

    /**
     * Returns user attribute mappings
     *
     * @param scimContext SCIM context
     * @return user attribute mappings
     */
    private List<UserAttribute<?>> getUserAttributeMappingList(ScimContext scimContext) {
        KeycloakSession session = scimContext.getSession();
        ScimConfig config = scimContext.getConfig();
        UserProfileProvider userProfileProvider = session.getProvider(UserProfileProvider.class);

        List<UserAttribute<?>> builtIn = List.of(
            new StringUserAttribute(
                UserAttribute.Source.USER_MODEL,
                config.getEmailAsUsername() ? UserModel.EMAIL : UserModel.USERNAME,
                "userName",
                "User name",
                SchemaAttribute.TypeEnum.STRING,
                SchemaAttribute.MutabilityEnum.READWRITE,
                SchemaAttribute.UniquenessEnum.SERVER,
                    config.getEmailAsUsername() ? UserModel::getEmail : UserModel::getUsername,
                    config.getEmailAsUsername() ? UserModel::setEmail : UserModel::setUsername
            ),
            new StringUserAttribute(
                UserAttribute.Source.USER_MODEL,
                UserModel.EMAIL,
                "email",
                "Email",
                SchemaAttribute.TypeEnum.STRING,
                SchemaAttribute.MutabilityEnum.READWRITE,
                SchemaAttribute.UniquenessEnum.SERVER,
                UserModel::getEmail,
                UserModel::setEmail
            ),
            new StringUserAttribute(
                UserAttribute.Source.USER_MODEL,
                UserModel.FIRST_NAME,
                "name.givenName",
                "First name",
                SchemaAttribute.TypeEnum.STRING,
                SchemaAttribute.MutabilityEnum.READWRITE,
                SchemaAttribute.UniquenessEnum.NONE,
                UserModel::getFirstName,
                UserModel::setFirstName
            ),
            new StringUserAttribute(
                UserAttribute.Source.USER_MODEL,
                UserModel.LAST_NAME,
                "name.familyName",
                "Family name",
                SchemaAttribute.TypeEnum.STRING,
                SchemaAttribute.MutabilityEnum.READWRITE,
                SchemaAttribute.UniquenessEnum.NONE,
                UserModel::getLastName,
                UserModel::setLastName
            ),
            new BooleanUserAttribute(
                UserAttribute.Source.USER_MODEL,
                UserModel.ENABLED,
                "active",
                "Whether user is active",
                SchemaAttribute.TypeEnum.BOOLEAN,
                SchemaAttribute.MutabilityEnum.READWRITE,
                SchemaAttribute.UniquenessEnum.NONE,
                UserModel::isEnabled,
                UserModel::setEnabled
            )
        );

        List<String> builtInAttributeNames = List.of(
            UserModel.USERNAME,
            UserModel.EMAIL,
            UserModel.FIRST_NAME,
            UserModel.LAST_NAME,
            UserModel.ENABLED
        );

        List<UserAttribute<String>> customAttributes = new ArrayList<>();

        if (userProfileProvider != null) {
            UPConfig userProfileConfiguration = userProfileProvider.getConfiguration();
            for (UPAttribute userProfileAttribute : userProfileConfiguration.getAttributes()) {
                if (!builtInAttributeNames.contains(userProfileAttribute.getName())) {
                    customAttributes.add(new StringUserAttribute(
                        UserAttribute.Source.USER_PROFILE,
                        userProfileAttribute.getName(),
                        userProfileAttribute.getName(),
                        userProfileAttribute.getName(),
                        SchemaAttribute.TypeEnum.STRING,
                        SchemaAttribute.MutabilityEnum.READWRITE,
                        SchemaAttribute.UniquenessEnum.NONE,
                        user -> user.getFirstAttribute(userProfileAttribute.getName()),
                        (user, value) -> user.setAttribute(userProfileAttribute.getName(), List.of(value))
                    ));
                }
            }
        }

        List<UserAttribute<?>> result = new ArrayList<>(builtIn);
        result.addAll(customAttributes);
        return result;
    }

    /**
     * Returns group schema attributes
     *
     * @return group schema attributes
     */
    private List<fi.metatavu.keycloak.scim.server.model.SchemaAttribute> getGroupSchemaAttributes() {
        return List.of(
            new SchemaAttribute()
                .name(GroupAttribute.DISPLAY_NAME.getScimPath())
                .description(GroupAttribute.DISPLAY_NAME.getScimPath())
                .type(SchemaAttribute.TypeEnum.STRING)
                .multiValued(false)
                .required(false)
                .caseExact(false)
                .mutability(SchemaAttribute.MutabilityEnum.READWRITE)
                .returned(SchemaAttribute.ReturnedEnum.DEFAULT)
                .referenceTypes(null)
                .subAttributes(Collections.emptyList())
                .uniqueness(SchemaAttribute.UniquenessEnum.NONE
            ),
            new SchemaAttribute()
                .name(GroupAttribute.MEMBERS.getScimPath())
                .description(GroupAttribute.MEMBERS.getScimPath())
                .type(SchemaAttribute.TypeEnum.COMPLEX)
                .multiValued(true)
                .required(false)
                .caseExact(false)
                .mutability(SchemaAttribute.MutabilityEnum.READWRITE)
                .returned(SchemaAttribute.ReturnedEnum.DEFAULT)
                .referenceTypes(null)
                .subAttributes(Collections.emptyList())
                .uniqueness(SchemaAttribute.UniquenessEnum.NONE)
        );
    }

    /**
     * Returns user schema attribute
     *
     * @param userAttribute user attribute mapping
     * @return schema attribute
     */
    private SchemaAttribute getUserSchemaAttribute(
        UserAttribute<?> userAttribute
    ) {
        SchemaAttribute result = new SchemaAttribute();
        result.setName(userAttribute.getScimPath());
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

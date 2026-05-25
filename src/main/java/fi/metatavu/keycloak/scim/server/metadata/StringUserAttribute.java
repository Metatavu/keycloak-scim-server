package fi.metatavu.keycloak.scim.server.metadata;

import fi.metatavu.keycloak.scim.server.model.SchemaAttribute;
import org.keycloak.models.UserModel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * User attribute for string values
 */
public class StringUserAttribute extends UserAttribute<String> {

    /**
     * Constructor without explicit remover. Defaults to {@code write(user, null)}.
     *
     * @param source source
     * @param sourceId source id
     * @param scimPath SCIM path
     * @param description description
     * @param type type
     * @param mutability mutability
     * @param uniqueness uniqueness
     * @param reader reader
     * @param writer writer
     */
    public StringUserAttribute(Source source, String sourceId, String scimPath, String description, SchemaAttribute.TypeEnum type, SchemaAttribute.MutabilityEnum mutability, SchemaAttribute.UniquenessEnum uniqueness, Function<UserModel, String> reader, BiConsumer<UserModel, String> writer) {
        super(source, sourceId, scimPath, description, type, mutability, uniqueness, reader, writer);
    }

    /**
     * Constructor with an explicit remover.
     *
     * @param source source
     * @param sourceId source id
     * @param scimPath SCIM path
     * @param description description
     * @param type type
     * @param mutability mutability
     * @param uniqueness uniqueness
     * @param reader reader
     * @param writer writer
     * @param remover remover
     */
    public StringUserAttribute(Source source, String sourceId, String scimPath, String description, SchemaAttribute.TypeEnum type, SchemaAttribute.MutabilityEnum mutability, SchemaAttribute.UniquenessEnum uniqueness, Function<UserModel, String> reader, BiConsumer<UserModel, String> writer, Consumer<UserModel> remover) {
        super(source, sourceId, scimPath, description, type, mutability, uniqueness, reader, writer, remover);
    }

}

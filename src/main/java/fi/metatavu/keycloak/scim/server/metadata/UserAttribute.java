package fi.metatavu.keycloak.scim.server.metadata;

import fi.metatavu.keycloak.scim.server.model.SchemaAttribute;
import org.keycloak.models.UserModel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * User attribute metadata
 * <p>
 * Class representing metadata for user attributes that are stored in Keycloak user model or user profile
 *
 * @param <T> attribute type
 */
public class UserAttribute <T> {

    /**
     * Attribute source
     */
    public enum Source {
        USER_MODEL,
        USER_PROFILE
    }

    private final Source source;
    private final String sourceId;
    private final String scimPath;
    private final String description;
    private final SchemaAttribute.TypeEnum type;
    private final SchemaAttribute.MutabilityEnum mutability;
    private final SchemaAttribute.UniquenessEnum uniqueness;
    private final Function<UserModel, T> reader;
    private final BiConsumer<UserModel, T> writer;
    private final Consumer<UserModel> remover;

    /**
     * Constructor without explicit remover. Defaults to {@code write(user, null)}.
     *
     * @param source attribute source
     * @param sourceId attribute source id
     * @param scimPath SCIM path
     * @param description attribute description
     * @param type attribute type
     * @param mutability attribute mutability
     * @param uniqueness attribute uniqueness
     * @param reader attribute reader
     * @param writer attribute writer
     */
    UserAttribute(
        Source source,
        String sourceId,
        String scimPath,
        String description,
        SchemaAttribute.TypeEnum type,
        SchemaAttribute.MutabilityEnum mutability,
        SchemaAttribute.UniquenessEnum uniqueness,
        Function<UserModel, T> reader,
        BiConsumer<UserModel, T> writer
    ) {
        this(source, sourceId, scimPath, description, type, mutability, uniqueness, reader, writer, null);
    }

    /**
     * Constructor with an explicit remover. Use when {@code write(user, null)} would be unsafe
     * (e.g. USER_PROFILE attributes backed by {@code user.setAttribute(name, List.of(value))}
     * which throws NPE on {@code List.of(null)}).
     *
     * @param source attribute source
     * @param sourceId attribute source id
     * @param scimPath SCIM path
     * @param description attribute description
     * @param type attribute type
     * @param mutability attribute mutability
     * @param uniqueness attribute uniqueness
     * @param reader attribute reader
     * @param writer attribute writer
     * @param remover attribute remover (nullable; falls back to {@code write(user, null)} when null)
     */
    UserAttribute(
        Source source,
        String sourceId,
        String scimPath,
        String description,
        SchemaAttribute.TypeEnum type,
        SchemaAttribute.MutabilityEnum mutability,
        SchemaAttribute.UniquenessEnum uniqueness,
        Function<UserModel, T> reader,
        BiConsumer<UserModel, T> writer,
        Consumer<UserModel> remover
    ) {
        this.source = source;
        this.sourceId = sourceId;
        this.scimPath = scimPath;
        this.description = description;
        this.type = type;
        this.mutability = mutability;
        this.uniqueness = uniqueness;
        this.reader = reader;
        this.writer = writer;
        this.remover = remover;
    }

    /**
     * Returns attribute source
     *
     * @return attribute source
     */
    public Source getSource() {
        return source;
    }

    /**
     * Returns SCIM path
     *
     * @return SCIM path
     */
    public String getScimPath() {
        return scimPath;
    }

    /**
     * Returns attribute source id
     *
     * @return attribute source id
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Returns attribute description
     *
     * @return attribute description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns attribute type
     *
     * @return attribute type
     */
    public SchemaAttribute.TypeEnum getType() {
        return type;
    }

    /**
     * Returns attribute mutability
     *
     * @return attribute mutability
     */
    public SchemaAttribute.MutabilityEnum getMutability() {
        return mutability;
    }

    /**
     * Returns attribute uniqueness
     *
     * @return attribute uniqueness
     */
    public SchemaAttribute.UniquenessEnum getUniqueness() {
        return uniqueness;
    }

    /**
     * Reads attribute value from user
     *
     * @param user user
     * @return attribute value
     */
    public T read(UserModel user) {
        return reader.apply(user);
    }

    /**
     * Writes attribute value to user
     *
     * @param user user
     * @param value attribute value
     */
    public void write(UserModel user, T value) {
        writer.accept(user, value);
    }

    /**
     * Removes this attribute from the user.
     * <p>
     * Uses the explicit remover when one was provided at construction time; otherwise falls back
     * to {@code write(user, null)}. USER_PROFILE attributes must supply an explicit remover
     * because their writer uses {@code List.of(value)}, which throws NPE when value is null.
     *
     * @param user user
     */
    public void clear(UserModel user) {
        if (remover != null) {
            remover.accept(user);
        } else {
            write(user, null);
        }
    }

}

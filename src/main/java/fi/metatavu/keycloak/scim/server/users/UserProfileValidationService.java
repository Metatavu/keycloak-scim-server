package fi.metatavu.keycloak.scim.server.users;

import fi.metatavu.keycloak.scim.server.metadata.UserAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.model.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.ValidationException;

/**
 * Service for validating SCIM user input against Keycloak user profile rules.
 */
public final class UserProfileValidationService {

    private static final Logger logger = Logger.getLogger(UserProfileValidationService.class);

    private UserProfileValidationService() {
    }

    /**
     * Validates a new SCIM user before creation.
     *
     * @param session Keycloak session
     * @param userAttributes user attribute metadata
     * @param scimUser SCIM user
     * @throws UserProfileValidationException when validation fails
     */
    public static void validateForCreate(
        KeycloakSession session,
        UserAttributes userAttributes,
        User scimUser
    ) throws UserProfileValidationException {
        validate(session, toUserProfileAttributes(userAttributes, scimUser));
    }

    /**
     * Validates a SCIM user update before modifying the existing user.
     *
     * @param session Keycloak session
     * @param userAttributes user attribute metadata
     * @param existing existing user
     * @param scimUser SCIM user
     * @throws UserProfileValidationException when validation fails
     */
    public static void validateForUpdate(
        KeycloakSession session,
        UserAttributes userAttributes,
        UserModel existing,
        User scimUser
    ) throws UserProfileValidationException {
        validate(session, toUserProfileAttributes(userAttributes, scimUser), existing);
    }

    /**
     * Validates patched attributes before applying them to the existing user.
     *
     * @param session Keycloak session
     * @param existing existing user
     * @param patchedAttributes patched user profile attributes
     * @throws UserProfileValidationException when validation fails
     */
    public static void validateForPatch(
        KeycloakSession session,
        UserAttributes userAttributes,
        UserModel existing,
        Map<String, ?> patchedAttributes
    ) throws UserProfileValidationException {
        Map<String, Object> attributes = toUserProfileAttributes(userAttributes, existing);
        attributes.putAll(patchedAttributes);
        validate(session, attributes, existing);
    }

    private static void validate(KeycloakSession session, Map<String, ?> attributes) throws UserProfileValidationException {
        UserProfileProvider profileProvider = getUserProfileProvider(session);
        if (profileProvider == null) {
            return;
        }

        try {
            profileProvider
                .create(UserProfileContext.USER_API, attributes)
                .validate();
        } catch (ValidationException e) {
            throw convertValidationException(e);
        }
    }

    private static void validate(KeycloakSession session, Map<String, ?> attributes, UserModel existing) throws UserProfileValidationException {
        UserProfileProvider profileProvider = getUserProfileProvider(session);
        if (profileProvider == null) {
            return;
        }

        try {
            profileProvider
                .create(UserProfileContext.USER_API, attributes, existing)
                .validate();
        } catch (ValidationException e) {
            throw convertValidationException(e);
        }
    }

    private static UserProfileProvider getUserProfileProvider(KeycloakSession session) {
        UserProfileProvider profileProvider = session.getProvider(UserProfileProvider.class);
        if (profileProvider == null) {
            logger.debug("UserProfileProvider not available, skipping validation");
            return null;
        }

        return profileProvider;
    }

    private static Map<String, Object> toUserProfileAttributes(UserAttributes userAttributes, User scimUser) {
        Map<String, Object> result = new HashMap<>();

        putScimAttribute(result, userAttributes, "userName", scimUser.getUserName());
        result.putIfAbsent(UserModel.USERNAME, normalizeValue(scimUser.getUserName()));
        putScimAttribute(result, userAttributes, "active", scimUser.getActive());

        if (scimUser.getName() != null) {
            putScimAttribute(result, userAttributes, "name.givenName", scimUser.getName().getGivenName());
            putScimAttribute(result, userAttributes, "name.familyName", scimUser.getName().getFamilyName());
        }

        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            putScimAttribute(result, userAttributes, "email", scimUser.getEmails().getFirst().getValue());
        }

        Map<String, Object> additionalProperties = scimUser.getAdditionalProperties();
        if (additionalProperties != null) {
            additionalProperties.forEach((key, value) -> putScimAttribute(result, userAttributes, key, value));
        }

        return result;
    }

    private static Map<String, Object> toUserProfileAttributes(UserAttributes userAttributes, UserModel user) {
        Map<String, Object> result = new HashMap<>();
        putUserModelAttributes(result, userAttributes, user, UserAttribute.Source.USER_MODEL);
        putUserModelAttributes(result, userAttributes, user, UserAttribute.Source.USER_PROFILE);
        putUserModelAttributes(result, userAttributes, user, UserAttribute.Source.IDP_MAPPER);
        result.putIfAbsent(UserModel.USERNAME, normalizeValue(user.getUsername()));

        return result;
    }

    private static void putUserModelAttributes(
        Map<String, Object> target,
        UserAttributes userAttributes,
        UserModel user,
        UserAttribute.Source source
    ) {
        userAttributes.listBySource(source).forEach(attribute -> target.put(attribute.getSourceId(), normalizeValue(attribute.read(user))));
    }

    private static void putScimAttribute(Map<String, Object> target, UserAttributes userAttributes, String scimPath, Object value) {
        UserAttribute<?> userAttribute = userAttributes.findByScimPath(scimPath);
        if (userAttribute != null) {
            target.put(userAttribute.getSourceId(), normalizeValue(value));
        }
    }

    /**
     * Converts SCIM PATCH values to shapes accepted by Keycloak user profile attributes.
     *
     * @param value SCIM value
     * @return normalized value
     */
    public static Object normalizeValue(Object value) {
        if (value == null || value instanceof String) {
            return value;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue.toString();
        }

        if (value instanceof List<?> values) {
            return values.stream()
                .map(String::valueOf)
                .toList();
        }

        return String.valueOf(value);
    }

    private static UserProfileValidationException convertValidationException(ValidationException e) {
        List<UserProfileValidationException.ValidationError> errors = e.getErrors().stream()
            .map(error -> new UserProfileValidationException.ValidationError(error.getAttribute(), error.getMessage()))
            .collect(Collectors.toList());

        if (errors.isEmpty()) {
            errors = List.of(new UserProfileValidationException.ValidationError(null, e.getMessage()));
        }

        return new UserProfileValidationException(errors);
    }

}
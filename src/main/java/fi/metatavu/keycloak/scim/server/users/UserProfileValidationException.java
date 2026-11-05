package fi.metatavu.keycloak.scim.server.users;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception thrown when Keycloak user profile validation fails.
 */
public class UserProfileValidationException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<ValidationError> errors;

    /**
     * Constructor.
     *
     * @param errors validation errors
     */
    public UserProfileValidationException(List<ValidationError> errors) {
        super(buildMessage(errors));
        this.errors = errors;
    }

    /**
     * Builds an exception message that names every failing attribute, so that log lines carry
     * the same detail as the SCIM error response.
     *
     * @param errors validation errors
     * @return message
     */
    private static String buildMessage(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "User profile validation failed";
        }

        return "User profile validation failed: " + errors.stream()
            .map(ValidationError::toString)
            .collect(Collectors.joining("; "));
    }

    /**
     * Returns validation errors.
     *
     * @return validation errors
     */
    public List<ValidationError> getErrors() {
        return errors;
    }

    /**
     * User profile validation error.
     *
     * @param attribute attribute on error
     * @param message validation message
     */
    public record ValidationError(String attribute, String message) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            if (attribute == null || attribute.isBlank()) {
                return message;
            }

            return String.format("%s: %s", attribute, message);
        }
    }

}
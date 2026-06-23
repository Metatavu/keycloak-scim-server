package fi.metatavu.keycloak.scim.server.users;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

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
        super("User profile validation failed");
        this.errors = errors;
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
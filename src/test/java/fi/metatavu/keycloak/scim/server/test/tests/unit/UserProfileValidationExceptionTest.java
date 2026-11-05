package fi.metatavu.keycloak.scim.server.test.tests.unit;

import fi.metatavu.keycloak.scim.server.users.UserProfileValidationException;
import fi.metatavu.keycloak.scim.server.users.UserProfileValidationException.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserProfileValidationExceptionTest {

    @Test
    void messageIncludesSingleAttributeError() {
        UserProfileValidationException e = new UserProfileValidationException(
            List.of(new ValidationError("emails", "Please specify email."))
        );

        assertEquals("User profile validation failed: emails: Please specify email.", e.getMessage());
    }

    @Test
    void messageIncludesAllAttributeErrors() {
        UserProfileValidationException e = new UserProfileValidationException(List.of(
            new ValidationError("emails", "Please specify email."),
            new ValidationError("userName", "Username contains invalid characters.")
        ));

        assertEquals(
            "User profile validation failed: emails: Please specify email.; userName: Username contains invalid characters.",
            e.getMessage()
        );
    }

    @Test
    void messageWithoutErrorsStaysGeneric() {
        UserProfileValidationException e = new UserProfileValidationException(List.of());

        assertEquals("User profile validation failed", e.getMessage());
    }

}

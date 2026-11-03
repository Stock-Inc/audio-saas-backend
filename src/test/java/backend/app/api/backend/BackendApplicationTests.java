package backend.app.api.backend;

import backend.app.api.backend.dto.LoginRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendApplicationTests {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void validLoginRequestPassesValidation() {
        var loginRequest = new LoginRequest(
                "user@example.com",
                "password"
        );

        assertTrue(validator.validate(loginRequest).isEmpty());
    }

    @Test
    void invalidLoginRequestFailsValidation() {
        var loginRequest = new LoginRequest("invalid", "short");

        assertEquals(2, validator.validate(loginRequest).size());
    }

}

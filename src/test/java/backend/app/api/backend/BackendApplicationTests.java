package backend.app.api.backend;

import backend.app.api.backend.dto.LoginRequest;
import backend.app.api.backend.dto.RegisterRequest;
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
    void validRequestsPassValidation() {
        var registerRequest = new RegisterRequest(
                "user",
                "user@example.com",
                "password"
        );
        var loginRequest = new LoginRequest(
                "user@example.com",
                "password"
        );

        assertTrue(validator.validate(registerRequest).isEmpty());
        assertTrue(validator.validate(loginRequest).isEmpty());
    }

    @Test
    void invalidRequestsFailValidation() {
        var registerRequest = new RegisterRequest("", "invalid", "short");
        var loginRequest = new LoginRequest("invalid", "short");

        assertEquals(3, validator.validate(registerRequest).size());
        assertEquals(2, validator.validate(loginRequest).size());
    }

}

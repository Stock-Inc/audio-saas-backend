
package backend.app.api.backend.controller;

import backend.app.api.backend.dto.RegisterRequest;
import backend.app.api.backend.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        registrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }
}

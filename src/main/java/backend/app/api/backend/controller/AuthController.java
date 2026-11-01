
package backend.app.api.backend.controller;

import backend.app.api.backend.dto.LoginRequest;
import backend.app.api.backend.dto.RegisterRequest;
import backend.app.api.backend.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        registrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request
    ) {
        registrationService.login(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @GetMapping("/me")
    public Map<String, String> me(
            @AuthenticationPrincipal OidcUser user
    ) {
        return Map.of(
                "id", Objects.requireNonNull(user.getSubject()),
                "username", Objects.requireNonNull(user.getPreferredUsername()),
                "email", Objects.requireNonNull(user.getEmail())
        );
    }
}

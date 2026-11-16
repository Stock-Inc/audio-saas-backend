package backend.app.api.backend.controller;

import backend.app.api.backend.dto.UserResponse;
import backend.app.api.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/api/v1/auth")
@Tag(
        name = "Авторизация",
        description = "Получение информации о пользователе, авторизованном через Keycloak"
)
public class AuthController {

    @GetMapping("/me")
    @Operation(
            summary = "Получить текущего пользователя",
            description = "Возвращает основные данные пользователя из текущей OIDC-сессии. "
                    + "Поддерживает backend-сессию и Bearer JWT. Без авторизации возвращает 401 и не запускает OAuth2-flow.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.SESSION_AUTH),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Пользователь успешно получен",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": "8f02cd17-f1ef-4c0f-a6fd-9eb57a6f7281",
                                              "username": "user123",
                                              "email": "user@example.com"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован или OIDC-сессия отсутствует",
                    content = @Content
            )
    })
    public ResponseEntity<UserResponse> me(
            @Parameter(hidden = true)
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Current user requested without an OIDC session");
            return ResponseEntity.status(401).build();
        }

        Object principal = authentication.getPrincipal();
        UserResponse response;

        if (principal instanceof OidcUser user) {
            response = new UserResponse(
                    user.getSubject(),
                    user.getPreferredUsername(),
                    user.getEmail()
            );
        } else if (principal instanceof Jwt jwt) {
            response = new UserResponse(
                    jwt.getSubject(),
                    jwt.getClaimAsString("preferred_username"),
                    jwt.getClaimAsString("email")
            );
        } else {
            log.warn(
                    "Current user has unsupported principal type: {}",
                    principal == null ? "null" : principal.getClass().getName()
            );
            return ResponseEntity.status(401).build();
        }

        log.debug("Current user requested: subject={}", response.id());

        return ResponseEntity.ok(response);
    }
}

package backend.app.api.backend.controller;

import backend.app.api.backend.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
                    + "Для вызова требуется активная авторизованная сессия."
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
            @AuthenticationPrincipal OidcUser user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                new UserResponse(
                        user.getSubject(),
                        user.getPreferredUsername(),
                        user.getEmail()
                )
        );
    }
}

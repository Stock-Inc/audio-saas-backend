package backend.app.api.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Основные данные текущего пользователя из Keycloak")
public record UserResponse(
        @Schema(
                description = "Стабильный идентификатор пользователя из OIDC claim sub",
                example = "8f02cd17-f1ef-4c0f-a6fd-9eb57a6f7281"
        )
        String id,

        @Schema(
                description = "Имя пользователя из OIDC claim preferred_username",
                example = "user123"
        )
        String username,

        @Schema(
                description = "Email пользователя из OIDC claim email",
                example = "user@example.com"
        )
        String email
) {}

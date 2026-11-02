package backend.app.api.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Данные для регистрации нового пользователя")
public record RegisterRequest(
        @Schema(
                description = "Уникальное имя пользователя",
                example = "sonora_user",
                minLength = 1
        )
        @NotBlank
        String username,

        @Schema(
                description = "Уникальный адрес электронной почты",
                example = "user@example.com",
                format = "email"
        )
        @NotBlank
        @Email
        String email,

        @Schema(
                description = "Пароль длиной не менее 8 символов",
                example = "strongPassword123",
                minLength = 8,
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotBlank
        @Size(min = 8)
        String password
) {}

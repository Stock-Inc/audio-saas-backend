package backend.app.api.backend.audio.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Имя пользовательской папки или тега")
public record CreateNamedItemRequest(
        @NotBlank
        @Size(max = 100)
        @Schema(example = "Встречи", maxLength = 100)
        String name
) {
}

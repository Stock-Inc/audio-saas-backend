package backend.app.api.backend.audio.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Параметры новой папки аудиотеки")
public record CreateFolderRequest(
        @NotBlank
        @Size(max = 100)
        @Schema(example = "Встречи", maxLength = 100)
        String name,

        @Schema(
                description = "ID родительской папки; null создаёт папку в корне",
                nullable = true
        )
        UUID parentId
) {
}

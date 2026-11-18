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
                description = "UUID родительской папки. Если поле отсутствует или равно null, папка создаётся в корне.",
                example = "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6a",
                nullable = true
        )
        UUID parentId
) {
}

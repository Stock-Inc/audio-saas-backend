package backend.app.api.backend.audio.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Новая папка файла")
public record MoveAudioRequest(
        @Schema(
                description = "UUID целевой папки. Если поле отсутствует или равно null, файл перемещается в корень.",
                example = "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6b",
                nullable = true
        )
        UUID folderId
) {
}

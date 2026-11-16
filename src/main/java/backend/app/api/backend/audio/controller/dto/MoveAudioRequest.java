package backend.app.api.backend.audio.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Новая папка файла")
public record MoveAudioRequest(
        @Schema(description = "ID папки; null перемещает файл в корень")
        UUID folderId
) {
}

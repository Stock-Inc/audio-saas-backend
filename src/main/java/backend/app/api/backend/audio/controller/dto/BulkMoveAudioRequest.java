package backend.app.api.backend.audio.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Массовое перемещение файлов")
public record BulkMoveAudioRequest(
        @NotEmpty
        @Schema(description = "Непустой набор ID загрузок")
        Set<UUID> audioIds,

        @Schema(
                description = "UUID целевой папки. Если поле отсутствует или равно null, файлы перемещаются в корень.",
                example = "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6b",
                nullable = true
        )
        UUID folderId
) {
}

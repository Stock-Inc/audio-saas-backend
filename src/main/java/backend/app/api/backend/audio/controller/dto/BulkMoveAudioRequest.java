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

        @Schema(description = "ID папки; null перемещает файлы в корень")
        UUID folderId
) {
}

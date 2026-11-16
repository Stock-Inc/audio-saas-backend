package backend.app.api.backend.audio.controller.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Полный набор тегов файла")
public record SetTagsRequest(
        @NotNull
        @Schema(description = "Пустой набор удаляет все теги")
        Set<UUID> tagIds
) {
}

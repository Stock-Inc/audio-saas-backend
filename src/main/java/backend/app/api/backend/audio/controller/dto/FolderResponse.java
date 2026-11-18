package backend.app.api.backend.audio.controller.dto;

import backend.app.api.backend.audio.controller.entity.AudioFolder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Папка аудиотеки")
public record FolderResponse(
        @Schema(description = "UUID папки", example = "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6b")
        UUID id,
        @Schema(description = "Имя папки", example = "Интервью")
        String name,
        @Schema(
                description = "UUID непосредственного родителя. У корневой папки значение null.",
                example = "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6a",
                nullable = true
        )
        UUID parentId,
        @Schema(description = "Дата и время создания", example = "2026-11-18T09:00:00Z")
        Instant createdAt,
        @Schema(description = "Дата и время последнего обновления", example = "2026-11-18T09:00:00Z")
        Instant updatedAt
) {
    public static FolderResponse from(AudioFolder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParent() == null ? null : folder.getParent().getId(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}

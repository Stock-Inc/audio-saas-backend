package backend.app.api.backend.audio.controller.dto;

import backend.app.api.backend.audio.controller.entity.AudioFolder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Папка аудиотеки")
public record FolderResponse(
        UUID id,
        String name,
        UUID parentId,
        Instant createdAt,
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

package backend.app.api.backend.audio.controller.dto;

import backend.app.api.backend.audio.controller.entity.AudioTag;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Тег аудиотеки")
public record TagResponse(
        UUID id,
        String name,
        Instant createdAt
) {
    public static TagResponse from(AudioTag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getCreatedAt()
        );
    }
}

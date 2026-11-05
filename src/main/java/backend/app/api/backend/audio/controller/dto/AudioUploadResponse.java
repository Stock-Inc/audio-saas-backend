package backend.app.api.backend.audio.controller.dto;

import backend.app.api.backend.audio.controller.entity.AudioStatus;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Результат приёма аудиофайла в обработку")
public record AudioUploadResponse(
        @Schema(
                description = "Внутренний идентификатор загрузки",
                example = "7747f2c4-c6c0-4b48-9e80-724bb8a85938"
        )
        UUID id,

        @Schema(
                description = "Идентификатор исходного запроса",
                example = "3e983854-c2ba-4d12-af8b-a8e2086c1370"
        )
        UUID requestId,

        @Schema(
                description = "Идентификатор аудиозаписи",
                example = "a96725b4-df5e-41bb-92c3-3ec3786f8621"
        )
        UUID recordId,

        @Schema(
                description = "Текущий статус загрузки",
                example = "FORWARDED"
        )
        AudioStatus status
) {

    public static AudioUploadResponse from(AudioUpload audio) {
        return new AudioUploadResponse(
                audio.getId(),
                audio.getRequestId(),
                audio.getRecordId(),
                audio.getStatus()
        );
    }
}

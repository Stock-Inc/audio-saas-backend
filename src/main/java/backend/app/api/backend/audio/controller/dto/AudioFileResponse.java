package backend.app.api.backend.audio.controller.dto;

import backend.app.api.backend.audio.controller.entity.AudioStatus;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Schema(description = "Аудиофайл в библиотеке текущего пользователя")
public record AudioFileResponse(
        @Schema(description = "ID загрузки")
        UUID id,
        @Schema(description = "Идемпотентный ID запроса во внешнем ML-сервисе")
        UUID requestId,
        @Schema(description = "ID записи для сопоставления Kafka-результата")
        UUID recordId,
        @Schema(description = "ID задания ML-сервиса; null до подтверждения")
        UUID processingJobId,
        @Schema(description = "Исходное имя файла")
        String originalFilename,
        @Schema(description = "MIME-тип исходного файла")
        String contentType,
        @Schema(description = "Размер исходного файла в байтах")
        long sizeBytes,
        AudioStatus status,
        @Schema(description = "Безопасное сообщение об ошибке; null без ошибки")
        String errorMessage,
        FolderResponse folder,
        List<TagResponse> tags,
        @Schema(description = "Можно ли скачивать /result")
        boolean resultAvailable,
        String resultFilename,
        Long resultSizeBytes,
        Instant createdAt,
        Instant updatedAt
) {
    public static AudioFileResponse from(AudioUpload audio) {
        List<TagResponse> tags = audio.getTags().stream()
                .sorted(Comparator.comparing(
                        tag -> tag.getName().toLowerCase()
                ))
                .map(TagResponse::from)
                .toList();

        return new AudioFileResponse(
                audio.getId(),
                audio.getRequestId(),
                audio.getRecordId(),
                audio.getProcessingJobId(),
                audio.getOriginalFilename(),
                audio.getContentType(),
                audio.getSizeBytes(),
                audio.getStatus(),
                audio.getErrorMessage(),
                audio.getFolder() == null
                        ? null
                        : FolderResponse.from(audio.getFolder()),
                tags,
                audio.getResultFilename() != null,
                audio.getResultFilename(),
                audio.getResultSizeBytes(),
                audio.getCreatedAt(),
                audio.getUpdatedAt()
        );
    }
}

package backend.app.api.backend.audio.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Schema(description = "Multipart-запрос на загрузку аудиофайла")
public record AudioUploadRequest(
        @Schema(
                description = "Идентификатор запроса во внешней системе",
                example = "3e983854-c2ba-4d12-af8b-a8e2086c1370",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UUID requestId,

        @Schema(
                description = "Идентификатор аудиозаписи во внешней системе",
                example = "a96725b4-df5e-41bb-92c3-3ec3786f8621",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UUID recordId,

        @Schema(
                description = "Аудиофайл",
                type = "string",
                format = "binary",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        MultipartFile file,

        @Schema(
                description = "Параметры обработки аудио в виде JSON-объекта",
                example = "{\"diarization\":true,\"summary\":true,\"tasks\":true}",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Map<String, Object> options
) {
}

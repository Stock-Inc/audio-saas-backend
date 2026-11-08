package backend.app.api.backend.audio.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Schema(description = "Multipart-запрос на загрузку аудиофайла")
public record AudioUploadRequest(
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

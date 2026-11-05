package backend.app.api.backend.audio.controller.controller;

import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import backend.app.api.backend.audio.controller.dto.AudioUploadRequest;
import backend.app.api.backend.audio.controller.dto.AudioUploadResponse;
import backend.app.api.backend.audio.controller.service.AudioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/audio")
@Tag(
        name = "Аудио",
        description = "Загрузка аудиофайлов и запуск их обработки"
)
public class AudioController {

    private final AudioService audioService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Загрузить аудиофайл",
            description = "Принимает аудиофайл и параметры обработки в формате multipart/form-data. "
                    + "После проверки сохраняет сведения о загрузке и передаёт данные сервису обработки.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = AudioUploadRequest.class),
                            encoding = @Encoding(
                                    name = "options",
                                    contentType = MediaType.APPLICATION_JSON_VALUE
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Файл принят в обработку",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AudioUploadResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": "7747f2c4-c6c0-4b48-9e80-724bb8a85938",
                                              "requestId": "3e983854-c2ba-4d12-af8b-a8e2086c1370",
                                              "recordId": "a96725b4-df5e-41bb-92c3-3ec3786f8621",
                                              "status": "FORWARDED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректный файл, UUID или options",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "Превышен допустимый размер multipart-запроса",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "Некорректный Content-Type файла или запроса",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Сервис обработки аудио недоступен",
                    content = @Content
            )
    })
    public ResponseEntity<AudioUploadResponse> upload(
            @Parameter(hidden = true)
            @RequestParam("requestId") UUID requestId,

            @Parameter(hidden = true)
            @RequestParam("recordId") UUID recordId,

            @Parameter(hidden = true)
            @RequestPart("file") MultipartFile file,

            @Parameter(hidden = true)
            @RequestPart("options") JsonNode options
    ) {
        AudioProcessingMetadata metadata = new AudioProcessingMetadata(
                requestId,
                recordId,
                options
        );

        return ResponseEntity
                .accepted()
                .body(audioService.upload(metadata, file));
    }
}

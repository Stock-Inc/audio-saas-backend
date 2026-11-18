package backend.app.api.backend.audio.controller.controller;

import backend.app.api.backend.audio.controller.dto.AudioFileResponse;
import backend.app.api.backend.audio.controller.dto.AudioSort;
import backend.app.api.backend.audio.controller.dto.BulkMoveAudioRequest;
import backend.app.api.backend.audio.controller.dto.MoveAudioRequest;
import backend.app.api.backend.audio.controller.dto.SetTagsRequest;
import backend.app.api.backend.audio.controller.service.AudioLibraryService;
import backend.app.api.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/audio")
@Tag(
        name = "Аудиотека",
        description = "Список аудиофайлов, метаданные, результаты, папки и теги"
)
@SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AudioLibraryController {

    private final AudioLibraryService libraryService;

    @GetMapping("/search")
    @Operation(
            summary = "Найти аудиофайлы по тексту",
            description = "Поиск по транскриптам и анализу текущего пользователя. "
                    + "Все значимые слова должны встречаться в одном результате. "
                    + "Возвращает до 50 файлов за запрос в порядке релевантности. Для следующей страницы увеличьте offset на 50."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Найденные файлы или пустой массив"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content)
    })
    public List<AudioFileResponse> search(
            @Parameter(description = "От 1 до 200 символов после удаления пробелов по краям", example = "план релиза")
            @RequestParam("q") String query,
            @Parameter(description = "Число пропускаемых результатов, от 0", example = "0")
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            Authentication authentication
    ) {
        return libraryService.searchAudio(authentication.getName(), query, offset);
    }

    @GetMapping
    @Operation(
            summary = "Получить аудиотеку",
            description = """
                    Фильтры folderId и tagId можно комбинировать. Если folderId отсутствует, поиск идёт по всей библиотеке.
                    При folderId=null возвращаются файлы в корне. Если совпадений нет, возвращается пустой массив.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список аудиофайлов"),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID фильтра или неизвестное значение sort", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content)
    })
    public List<AudioFileResponse> list(
            Authentication authentication,
            @Parameter(description = "UUID папки или строка null для файлов в корне", example = "null")
            @RequestParam(required = false) String folderId,
            @Parameter(description = "Фильтр по тегу")
            @RequestParam(required = false) UUID tagId,
            @Parameter(
                    description = """
                            NEWEST (по умолчанию) и OLDEST сортируют по дате загрузки.
                            NAME_ASC и NAME_DESC сортируют по имени без учёта регистра.
                            SIZE_ASC и SIZE_DESC сортируют по размеру в байтах.
                            При равных значениях порядок определяется UUID файла.
                            """,
                    example = "NAME_ASC"
            )
            @RequestParam(name = "sort", defaultValue = "NEWEST") AudioSort sort
    ) {
        if (!StringUtils.hasText(folderId)) {
            return libraryService.audioFiles(authentication.getName(), null, tagId, false, sort);
        }
        if ("null".equals(folderId)) {
            return libraryService.audioFiles(authentication.getName(), null, tagId, true, sort);
        }

        UUID folderUuid;
        try {
            folderUuid = UUID.fromString(folderId.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid folderId", ex);
        }
        return libraryService.audioFiles(authentication.getName(), folderUuid, tagId, true, sort);
    }

    @GetMapping("/{audioId}")
    @Operation(summary = "Получить аудиофайл")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Метаданные аудиофайла"),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID загрузки", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "404", description = "Файл не найден", content = @Content)
    })
    public AudioFileResponse get(
            @Parameter(description = "ID загрузки")
            @PathVariable UUID audioId,
            Authentication authentication
    ) {
        return libraryService.audioFile(authentication.getName(), audioId);
    }

    @GetMapping("/{audioId}/result")
    @Operation(
            summary = "Скачать результат обработки",
            description = "Возвращает JSON-файл как attachment. Пока результат недоступен, отвечает 409."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "JSON-файл с транскриптом и анализом",
                    headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                            description = "Attachment с именем результата",
                            schema = @Schema(type = "string", example = "attachment; filename=\"audio-result.json\"")),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID загрузки", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "404", description = "Файл не найден", content = @Content),
            @ApiResponse(responseCode = "409", description = "Результат ещё не готов", content = @Content)
    })
    public ResponseEntity<byte[]> downloadResult(
            @PathVariable UUID audioId,
            Authentication authentication
    ) {
        AudioLibraryService.ResultFile result = libraryService.result(
                authentication.getName(),
                audioId
        );

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(result.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.data().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .body(result.data());
    }

    @PutMapping("/{audioId}/folder")
    @Operation(
            summary = "Переместить файл в папку",
            description = "Передайте UUID целевой папки или JSON null в folderId для перемещения в корень.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MoveAudioRequest.class),
                            examples = {
                                    @ExampleObject(name = "В папку", value = "{\"folderId\":\"6de80c36-6c03-4a2e-8c03-57f8f1bf1b6b\"}"),
                                    @ExampleObject(name = "В корень", value = "{\"folderId\":null}")
                            })
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Файл перемещён"),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID или тело запроса", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "404", description = "Файл или папка не найдены", content = @Content)
    })
    public AudioFileResponse move(
            @PathVariable UUID audioId,
            @Valid @RequestBody MoveAudioRequest request,
            Authentication authentication
    ) {
        return libraryService.move(
                authentication.getName(),
                audioId,
                request.folderId()
        );
    }

    @PutMapping("/folder")
    @Operation(
            summary = "Переместить несколько файлов",
            description = "Передайте UUID целевой папки или JSON null в folderId для перемещения в корень. "
                    + "Если хотя бы один файл или папка недоступны пользователю, расположение файлов сохраняется.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BulkMoveAudioRequest.class),
                            examples = @ExampleObject(name = "В корень", value = """
                                    {
                                      "audioIds": ["7747f2c4-c6c0-4b48-9e80-724bb8a85938"],
                                      "folderId": null
                                    }
                                    """))
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Перемещённые файлы"),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID либо audioIds отсутствует или пуст", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "404", description = "Один из файлов или папка не найдены", content = @Content)
    })
    public List<AudioFileResponse> moveAll(
            @Valid @RequestBody BulkMoveAudioRequest request,
            Authentication authentication
    ) {
        return libraryService.moveAll(
                authentication.getName(),
                request.audioIds(),
                request.folderId()
        );
    }

    @PutMapping("/{audioId}/tags")
    @Operation(
            summary = "Заменить теги файла",
            description = "Полностью заменяет набор тегов файла переданным массивом. Пустой tagIds удаляет все привязки.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SetTagsRequest.class),
                            examples = {
                                    @ExampleObject(name = "Назначить теги", value = "{\"tagIds\":[\"0fc4a6d0-00c8-4fed-81bb-2c9f0639525e\"]}"),
                                    @ExampleObject(name = "Очистить теги", value = "{\"tagIds\":[]}")
                            })
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Обновлённый файл"),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID либо tagIds отсутствует или равен null", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "404", description = "Файл или один из тегов не найдены", content = @Content)
    })
    public AudioFileResponse replaceTags(
            @PathVariable UUID audioId,
            @Valid @RequestBody SetTagsRequest request,
            Authentication authentication
    ) {
        return libraryService.replaceTags(
                authentication.getName(),
                audioId,
                request.tagIds()
        );
    }
}

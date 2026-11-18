package backend.app.api.backend.audio.controller.controller;

import backend.app.api.backend.audio.controller.dto.AudioFileResponse;
import backend.app.api.backend.audio.controller.dto.BulkMoveAudioRequest;
import backend.app.api.backend.audio.controller.dto.MoveAudioRequest;
import backend.app.api.backend.audio.controller.dto.SetTagsRequest;
import backend.app.api.backend.audio.controller.service.AudioLibraryService;
import backend.app.api.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
                    + "Возвращает до 50 файлов за запрос в порядке релевантности."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Найденные файлы или пустой массив"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content)
    })
    public List<AudioFileResponse> search(
            @Parameter(description = "От 1 до 200 символов после удаления пробелов по краям", example = "план релиза")
            @RequestParam("q") String query,
            @Parameter(description = "Число пропускаемых результатов", example = "0")
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            Authentication authentication
    ) {
        return libraryService.searchAudio(authentication.getName(), query, offset);
    }

    @GetMapping
    @Operation(
            summary = "Получить аудиотеку",
            description = "Возвращает файлы текущего пользователя, новые сначала. Фильтры folderId и tagId можно комбинировать."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список аудиофайлов"),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content)
    })
    public List<AudioFileResponse> list(
            Authentication authentication,
            @Parameter(description = "Фильтр по папке")
            @RequestParam(required = false) UUID folderId,
            @Parameter(description = "Фильтр по тегу")
            @RequestParam(required = false) UUID tagId
    ) {
        return libraryService.audioFiles(
                authentication.getName(),
                folderId,
                tagId
        );
    }

    @GetMapping("/{audioId}")
    @Operation(summary = "Получить аудиофайл")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Метаданные аудиофайла"),
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
            description = "Возвращает JSON-файл как attachment. До статуса COMPLETED отвечает 409."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "JSON-файл с транскриптом и анализом",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
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
            description = "Передайте folderId=null, чтобы убрать файл из папки."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Файл перемещён"),
            @ApiResponse(responseCode = "400", description = "Некорректное тело запроса", content = @Content),
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
            description = "Операция атомарна: если хотя бы один файл или папка недоступны пользователю, ничего не перемещается."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Перемещённые файлы"),
            @ApiResponse(responseCode = "400", description = "audioIds отсутствует или пуст", content = @Content),
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
            description = "Передайте пустой tagIds, чтобы удалить все теги файла."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Обновлённый файл"),
            @ApiResponse(responseCode = "400", description = "tagIds отсутствует", content = @Content),
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

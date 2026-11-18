package backend.app.api.backend.audio.controller.controller;

import backend.app.api.backend.audio.controller.dto.CreateNamedItemRequest;
import backend.app.api.backend.audio.controller.dto.TagResponse;
import backend.app.api.backend.audio.controller.service.AudioLibraryService;
import backend.app.api.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tags")
@Tag(name = "Теги", description = "Пользовательские теги аудиотеки")
@SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TagController {

    private final AudioLibraryService libraryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Создать тег",
            description = "Имя тега должно быть уникальным среди тегов текущего пользователя без учёта регистра."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Тег создан"),
            @ApiResponse(responseCode = "400", description = "Имя пустое или длиннее 100 символов", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "409", description = "Тег с таким именем уже существует", content = @Content)
    })
    public TagResponse create(
            @Valid @RequestBody CreateNamedItemRequest request,
            Authentication authentication
    ) {
        return libraryService.createTag(
                authentication.getName(),
                request.name()
        );
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Переименовать тег",
            description = "Сохраняет UUID тега и привязки к файлам. Сервер обрезает пробелы по краям имени.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateNamedItemRequest.class),
                            examples = @ExampleObject(name = "Переименование", value = "{\"name\":\"Срочное\"}"))
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Обновлённый тег", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TagResponse.class),
                    examples = @ExampleObject(name = "Обновлённый тег", value = """
                            {
                              "id": "0fc4a6d0-00c8-4fed-81bb-2c9f0639525e",
                              "name": "Срочное",
                              "createdAt": "2026-11-18T08:00:00Z"
                            }
                            """)
            )),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID или имя", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "404", description = "Тег отсутствует или недоступен", content = @Content),
            @ApiResponse(responseCode = "409", description = "Имя уже занято другим тегом пользователя без учёта регистра", content = @Content)
    })
    public TagResponse rename(
            @Parameter(description = "UUID тега", example = "0fc4a6d0-00c8-4fed-81bb-2c9f0639525e")
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateNamedItemRequest request,
            Authentication authentication
    ) {
        return libraryService.renameTag(authentication.getName(), id, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить тег", description = "Удаляет тег и его привязки. Аудиофайлы остаются в библиотеке.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Тег удалён, тело ответа отсутствует", content = @Content),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "404", description = "Тег отсутствует или недоступен", content = @Content)
    })
    public void delete(
            @Parameter(description = "UUID тега", example = "0fc4a6d0-00c8-4fed-81bb-2c9f0639525e")
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        libraryService.deleteTag(authentication.getName(), id);
    }

    @GetMapping
    @Operation(summary = "Получить теги")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Теги по имени"),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content)
    })
    public List<TagResponse> list(Authentication authentication) {
        return libraryService.tags(authentication.getName());
    }
}

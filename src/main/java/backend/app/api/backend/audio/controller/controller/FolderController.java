package backend.app.api.backend.audio.controller.controller;

import backend.app.api.backend.audio.controller.dto.CreateFolderRequest;
import backend.app.api.backend.audio.controller.dto.CreateNamedItemRequest;
import backend.app.api.backend.audio.controller.dto.FolderResponse;
import backend.app.api.backend.audio.controller.service.AudioLibraryService;
import backend.app.api.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
@RequestMapping("/api/v1/folders")
@Tag(name = "Папки", description = "Пользовательские папки аудиотеки")
@SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class FolderController {

    private final AudioLibraryService libraryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать папку")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Папка создана"),
            @ApiResponse(responseCode = "400", description = "Имя пустое или длиннее 100 символов", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "404", description = "Родительская папка не найдена", content = @Content),
            @ApiResponse(responseCode = "409", description = "Папка с таким именем уже существует у выбранного родителя", content = @Content)
    })
    public FolderResponse create(
            @Valid @RequestBody CreateFolderRequest request,
            Authentication authentication
    ) {
        return libraryService.createFolder(
                authentication.getName(),
                request.name(),
                request.parentId()
        );
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename folder")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated folder"),
            @ApiResponse(responseCode = "400", description = "Invalid UUID or name", content = @Content),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
            @ApiResponse(responseCode = "404", description = "Folder unavailable", content = @Content),
            @ApiResponse(responseCode = "409", description = "Name already exists", content = @Content)
    })
    public FolderResponse rename(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateNamedItemRequest request,
            Authentication authentication
    ) {
        return libraryService.renameFolder(authentication.getName(), id, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete folder", description = "Moves audio files to the library root. A folder with subfolders cannot be deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Folder deleted", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
            @ApiResponse(responseCode = "409", description = "Folder has subfolders", content = @Content),
            @ApiResponse(responseCode = "404", description = "Folder unavailable", content = @Content)
    })
    public void delete(@PathVariable("id") UUID id, Authentication authentication) {
        libraryService.deleteFolder(authentication.getName(), id);
    }

    @GetMapping
    @Operation(
            summary = "Получить папки",
            description = """
                    Возвращает плоский список всех папок текущего пользователя, отсортированный по имени.
                    Для отображения корня выберите элементы с parentId = null.
                    Для открытия папки выберите элементы, у которых parentId совпадает с id открытой папки.
                    Повторите тот же шаг для каждого следующего уровня.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Папки по имени", content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = FolderResponse.class)),
                    examples = @ExampleObject(name = "Папка с дочерней папкой", value = """
                            [
                              {
                                "id": "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6a",
                                "name": "Встречи",
                                "parentId": null,
                                "createdAt": "2026-11-18T08:00:00Z",
                                "updatedAt": "2026-11-18T08:00:00Z"
                              },
                              {
                                "id": "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6b",
                                "name": "Интервью",
                                "parentId": "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6a",
                                "createdAt": "2026-11-18T09:00:00Z",
                                "updatedAt": "2026-11-18T09:00:00Z"
                              }
                            ]
                            """)
            )),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content)
    })
    public List<FolderResponse> list(Authentication authentication) {
        return libraryService.folders(authentication.getName());
    }

    @GetMapping("/{id}/children")
    @Operation(
            summary = "Получить подпапки",
            description = """
                    Возвращает только непосредственные подпапки папки с указанным UUID,
                    отсортированные по имени. Если подпапок нет, возвращает пустой массив.
                    Для перехода на следующий уровень передайте UUID нужной подпапки.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Подпапки по имени", content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = FolderResponse.class))
            )),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID папки", content = @Content),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content),
            @ApiResponse(responseCode = "404", description = "Папка отсутствует или недоступна", content = @Content)
    })
    public List<FolderResponse> children(@PathVariable("id") UUID id, Authentication authentication) {
        return libraryService.childFolders(authentication.getName(), id);
    }

    @GetMapping("/null/children")
    @Operation(
            summary = "Получить папки верхнего уровня",
            description = """
                    Возвращает папки текущего пользователя с parentId = null,
                    отсортированные по имени. Если таких папок нет, возвращает пустой массив.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Папки по имени", content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = FolderResponse.class))
            )),
            @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content)
    })
    public List<FolderResponse> rootChildren(Authentication authentication) {
        return libraryService.childFolders(authentication.getName(), null);
    }
}

package backend.app.api.backend.audio.controller.controller;

import backend.app.api.backend.audio.controller.dto.CreateNamedItemRequest;
import backend.app.api.backend.audio.controller.dto.TagResponse;
import backend.app.api.backend.audio.controller.service.AudioLibraryService;
import backend.app.api.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
    @Operation(summary = "Создать тег")
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
    @Operation(summary = "Rename tag")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated tag"),
            @ApiResponse(responseCode = "400", description = "Invalid UUID or name", content = @Content),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
            @ApiResponse(responseCode = "404", description = "Tag unavailable", content = @Content),
            @ApiResponse(responseCode = "409", description = "Name already exists", content = @Content)
    })
    public TagResponse rename(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateNamedItemRequest request,
            Authentication authentication
    ) {
        return libraryService.renameTag(authentication.getName(), id, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete tag", description = "Removes the tag from all audio files.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tag deleted", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
            @ApiResponse(responseCode = "404", description = "Tag unavailable", content = @Content)
    })
    public void delete(@PathVariable("id") UUID id, Authentication authentication) {
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

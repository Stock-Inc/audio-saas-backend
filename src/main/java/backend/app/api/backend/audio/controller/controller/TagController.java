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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

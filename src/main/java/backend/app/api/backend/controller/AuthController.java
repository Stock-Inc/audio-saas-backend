
package backend.app.api.backend.controller;

import backend.app.api.backend.dto.RegisterRequest;
import backend.app.api.backend.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Авторизация", description = "Регистрация и авторизация пользователей")
public class AuthController {

    private final RegistrationService registrationService;

    @PostMapping("/register")
    @Operation(
            summary = "Зарегистрировать пользователя",
            description = "Создаёт активную учётную запись в Keycloak. "
                    + "Адрес электронной почты и имя пользователя должны быть уникальными."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Пользователь зарегистрирован"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Тело запроса не прошло валидацию или Keycloak отклонил данные",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Пользователь с таким именем или email уже существует",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Сервис авторизации Keycloak недоступен или вернул ошибку",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        registrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }
}

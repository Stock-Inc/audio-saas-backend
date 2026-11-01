
package backend.app.api.backend.service;

import backend.app.api.backend.dto.LoginRequest;
import backend.app.api.backend.dto.RegisterRequest;
import backend.app.api.backend.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class RegistrationService {

    @Value("${keycloak.admin.base-url}")
    private String baseUrl;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.client-secret}")
    private String clientSecret;

    private final RestClient client;

    private final String realm;

    public RegistrationService(
            RestClient.Builder builder,
            @Value("${keycloak.admin.base-url}") String baseUrl,
            @Value("${keycloak.admin.realm}") String realm
    ) {
        this.client = builder
                .baseUrl(baseUrl)
                .build();

        this.realm = realm;
    }

    public void register(RegisterRequest request) {
        String token = getAdminToken();

        Map<String, Object> user = Map.of(
                "username", request.username(),
                "email", request.email(),
                "enabled", true,
                "emailVerified", false,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", request.password(),
                        "temporary", false
                ))
        );

        try {
            client.post()
                    .uri(baseUrl + "/admin/realms/" + realm + "/users")
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();

            if (status == 409) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Пользователь уже существует"
                );
            }

            if (status == 400) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Некорректные данные регистрации"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Ошибка при регистрации в Keycloak"
            );
        }
    }

    private String getAdminToken() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        try {
            TokenResponse response = client.post()
                    .uri(baseUrl + "/realms/" + realm
                            + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Keycloak не вернул access token"
                );
            }

            return response.accessToken();

        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Не удалось получить сервисный токен Keycloak"
            );
        }
    }
}

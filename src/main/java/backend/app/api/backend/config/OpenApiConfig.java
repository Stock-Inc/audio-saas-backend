package backend.app.api.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String SESSION_AUTH = "sessionAuth";

    @Bean
    public OpenAPI sonoraOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sonora Audio SaaS API")
                        .description("REST API сервиса Sonora для работы с учётными записями и аудио.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Sonora API team"))
                        .license(new License()
                                .name("Proprietary")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .name(BEARER_AUTH)
                                        .description(
                                                "JWT access token issued by Keycloak"
                                        )
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                        .addSecuritySchemes(
                                SESSION_AUTH,
                                new SecurityScheme()
                                        .name("JSESSIONID")
                                        .description(
                                                "Backend session cookie created by the Keycloak OAuth2 login flow"
                                        )
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                        ))
                .servers(List.of(new Server()
                        .url("/")
                        .description("Текущий сервер")));
    }
}

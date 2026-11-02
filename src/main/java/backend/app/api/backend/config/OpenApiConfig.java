package backend.app.api.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

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
                .servers(List.of(new Server()
                        .url("/")
                        .description("Текущий сервер")));
    }
}

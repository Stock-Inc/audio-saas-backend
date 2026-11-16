package backend.app.api.backend.config;

import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiConfigTests {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void openApiContainsServiceMetadataAndRelativeServer() {
        var openApi = config.sonoraOpenApi();

        assertEquals("Sonora Audio SaaS API", openApi.getInfo().getTitle());
        assertEquals("v1", openApi.getInfo().getVersion());
        assertEquals("Sonora API team", openApi.getInfo().getContact().getName());
        assertEquals("Proprietary", openApi.getInfo().getLicense().getName());
        assertEquals("/", openApi.getServers().getFirst().getUrl());
    }

    @Test
    void openApiDefinesJwtBearerAuthentication() {
        var openApi = config.sonoraOpenApi();
        SecurityScheme bearerAuth = openApi.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH);

        assertNotNull(bearerAuth);
        assertEquals(SecurityScheme.Type.HTTP, bearerAuth.getType());
        assertEquals("bearer", bearerAuth.getScheme());
        assertEquals("JWT", bearerAuth.getBearerFormat());
    }

    @Test
    void openApiDefinesBackendSessionAuthentication() {
        var openApi = config.sonoraOpenApi();
        SecurityScheme sessionAuth = openApi.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.SESSION_AUTH);

        assertNotNull(sessionAuth);
        assertEquals(SecurityScheme.Type.APIKEY, sessionAuth.getType());
        assertEquals(SecurityScheme.In.COOKIE, sessionAuth.getIn());
        assertEquals("JSESSIONID", sessionAuth.getName());
    }
}

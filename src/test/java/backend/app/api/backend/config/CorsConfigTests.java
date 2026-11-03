package backend.app.api.backend.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTests {

    @Test
    void configuresFrontendOriginsAndCredentials() {
        var source = new CorsConfig().corsConfigurationSource(List.of(
                " http://localhost:3000 ",
                "https://app.example.com"
        ));
        var request = new MockHttpServletRequest(
                "OPTIONS",
                "/api/v1/auth/me"
        );

        var configuration = source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertEquals(
                List.of(
                        "http://localhost:3000",
                        "https://app.example.com"
                ),
                configuration.getAllowedOrigins()
        );
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
        assertTrue(configuration.getAllowedMethods().contains("OPTIONS"));
    }
}

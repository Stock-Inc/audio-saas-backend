package backend.app.api.backend.controller;

import backend.app.api.backend.config.CorsConfig;
import backend.app.api.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = {
        "KEYCLOAK_CLIENT_SECRET=test-client-secret",
        "CORS_ALLOWED_ORIGINS=http://localhost:3000"
})
class AuthControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meWithoutOidcSessionReturnsUnauthorizedWithoutStartingLogin()
            throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void meWithOidcSessionReturnsCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(oidcLogin().idToken(token -> token
                                .subject("keycloak-user-id")
                                .claim("preferred_username", "user123")
                                .claim("email", "user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("keycloak-user-id"))
                .andExpect(jsonPath("$.username").value("user123"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void explicitLoginEntryStillRedirectsToKeycloak() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/keycloak"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        startsWith(
                                "http://localhost:9090/realms/demo/"
                                        + "protocol/openid-connect/auth?"
                        )
                ));
    }
}

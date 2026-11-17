package backend.app.api.backend.audio.controller.controller;

import backend.app.api.backend.audio.controller.dto.FolderResponse;
import backend.app.api.backend.audio.controller.service.AudioLibraryService;
import backend.app.api.backend.config.OpenApiConfig;
import backend.app.api.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        AudioLibraryController.class,
        FolderController.class,
        TagController.class
})
@Import({SecurityConfig.class, OpenApiConfig.class})
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class,
        SwaggerConfig.class,
        SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class
})
@TestPropertySource(properties = "KEYCLOAK_CLIENT_SECRET=test-client-secret")
class AudioLibraryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AudioLibraryService libraryService;

    @Test
    void resultEndpointReturnsAttachment() throws Exception {
        UUID audioId = UUID.randomUUID();
        byte[] body = "{\"status\":\"COMPLETED\"}"
                .getBytes(StandardCharsets.UTF_8);
        when(libraryService.result(anyString(), eq(audioId)))
                .thenReturn(new AudioLibraryService.ResultFile(
                        "meeting-result.json",
                        MediaType.APPLICATION_JSON_VALUE,
                        body
                ));

        mockMvc.perform(get("/api/v1/audio/{audioId}/result", audioId)
                        .with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(body))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("meeting-result.json")
                ));
    }

    @Test
    void createFolderUsesAuthenticatedOwner() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(libraryService.createFolder(anyString(), eq("Meetings"), isNull()))
                .thenReturn(new FolderResponse(
                        folderId,
                        "Meetings",
                        null,
                        null,
                        null
                ));

        mockMvc.perform(post("/api/v1/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Meetings\"}")
                        .with(oidcLogin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(folderId.toString()))
                .andExpect(jsonPath("$.name").value("Meetings"));

        verify(libraryService).createFolder(anyString(), eq("Meetings"), isNull());
    }

    @Test
    void createNestedFolderPassesParentId() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(libraryService.createFolder(
                anyString(),
                eq("Interviews"),
                eq(parentId)
        )).thenReturn(new FolderResponse(
                folderId,
                "Interviews",
                parentId,
                null,
                null
        ));

        mockMvc.perform(post("/api/v1/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Interviews",
                                  "parentId": "%s"
                                }
                                """.formatted(parentId))
                        .with(oidcLogin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(folderId.toString()))
                .andExpect(jsonPath("$.parentId").value(parentId.toString()));

        verify(libraryService).createFolder(
                anyString(),
                eq("Interviews"),
                eq(parentId)
        );
    }

    @Test
    void libraryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/audio"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generatedOpenApiContainsLibraryFolderAndTagContracts()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/audio'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/audio/{audioId}'].get")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/audio/{audioId}/result'].get.responses['409']"
                ).exists())
                .andExpect(jsonPath("$.paths['/api/v1/audio/folder'].put")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/folders'].post.responses['409']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/tags'].get")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AudioFileResponse")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/audio'].get.security[0].sessionAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/audio'].get.security[1].bearerAuth"
                ).isArray());
    }
}

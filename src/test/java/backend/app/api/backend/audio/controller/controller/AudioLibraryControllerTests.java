package backend.app.api.backend.audio.controller.controller;

import backend.app.api.backend.audio.controller.dto.AudioSort;
import backend.app.api.backend.audio.controller.dto.FolderResponse;
import backend.app.api.backend.audio.controller.dto.TagResponse;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void searchPassesQueryAndOffsetForAuthenticatedUser() throws Exception {
        when(libraryService.searchAudio(anyString(), eq("план релиза"), eq(50)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/audio/search")
                        .param("q", "план релиза")
                        .param("offset", "50")
                        .with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(libraryService).searchAudio(anyString(), eq("план релиза"), eq(50));
    }

    @Test
    void audioListDistinguishesRootFolderFromOmittedFolder() throws Exception {
        UUID tagId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/audio").with(oidcLogin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/audio").param("folderId", "null").with(oidcLogin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/audio").param("folderId", "null")
                        .param("tagId", tagId.toString()).with(oidcLogin()))
                .andExpect(status().isOk());

        verify(libraryService).audioFiles(anyString(), isNull(), isNull(), eq(false), eq(AudioSort.NEWEST));
        verify(libraryService).audioFiles(anyString(), isNull(), isNull(), eq(true), eq(AudioSort.NEWEST));
        verify(libraryService).audioFiles(anyString(), isNull(), eq(tagId), eq(true), eq(AudioSort.NEWEST));
    }

    @Test
    void audioListAcceptsUuidAndRejectsInvalidFolderFilter() throws Exception {
        UUID folderId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/audio").param("folderId", folderId.toString()).with(oidcLogin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/audio").param("folderId", "invalid").with(oidcLogin()))
                .andExpect(status().isBadRequest());

        verify(libraryService).audioFiles(anyString(), eq(folderId), isNull(), eq(true), eq(AudioSort.NEWEST));
    }

    @Test
    void audioListAcceptsExplicitSortAndRejectsUnknownSort() throws Exception {
        mockMvc.perform(get("/api/v1/audio").param("sort", "NAME_ASC").with(oidcLogin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/audio").param("sort", "unknown").with(oidcLogin()))
                .andExpect(status().isBadRequest());

        verify(libraryService).audioFiles(anyString(), isNull(), isNull(), eq(false), eq(AudioSort.NAME_ASC));
    }

    @Test
    void folderAndTagCanBeRenamedAndDeleted() throws Exception {
        UUID id = UUID.randomUUID();
        when(libraryService.renameFolder(anyString(), eq(id), eq("New name")))
                .thenReturn(new FolderResponse(id, "New name", null, null, null));
        when(libraryService.renameTag(anyString(), eq(id), eq("New name")))
                .thenReturn(new TagResponse(id, "New name", null));

        for (String resource : List.of("folders", "tags")) {
            mockMvc.perform(patch("/api/v1/" + resource + "/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"New name"}
                                    """)
                            .with(oidcLogin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New name"));
            mockMvc.perform(delete("/api/v1/" + resource + "/{id}", id).with(oidcLogin()))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
            mockMvc.perform(delete("/api/v1/" + resource + "/{id}", id))
                    .andExpect(status().isUnauthorized());
        }

        verify(libraryService).deleteFolder(anyString(), eq(id));
        verify(libraryService).deleteTag(anyString(), eq(id));
    }

    @Test
    void childrenEndpointReturnsDirectChildFolders() throws Exception {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(libraryService.childFolders(anyString(), eq(parentId)))
                .thenReturn(List.of(new FolderResponse(childId, "Interviews", parentId, null, null)));

        mockMvc.perform(get("/api/v1/folders/{id}/children", parentId).with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(childId.toString()))
                .andExpect(jsonPath("$[0].parentId").value(parentId.toString()));
    }

    @Test
    void childrenEndpointAcceptsNullForRootFolders() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(libraryService.childFolders(anyString(), eq((UUID) null)))
                .thenReturn(List.of(new FolderResponse(folderId, "Meetings", null, null, null)));

        mockMvc.perform(get("/api/v1/folders/null/children").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(folderId.toString()));
    }

    @Test
    void childrenEndpointRejectsInvalidFolderId() throws Exception {
        mockMvc.perform(get("/api/v1/folders/invalid/children").with(oidcLogin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openApiDocumentsFolderChildrenEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/folders/{id}/children'].get.responses['200'].content['application/json'].schema.type")
                        .value("array"))
                .andExpect(jsonPath("$.paths['/api/v1/folders/null/children'].get.responses['200'].content['application/json'].schema.type")
                        .value("array"));
    }

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
                .andExpect(jsonPath("$.paths['/api/v1/folders/{id}'].patch.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/folders/{id}'].delete.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tags/{id}'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tags/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/audio'].get.parameters[?(@.name == 'sort')].schema.enum",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasItem("NAME_ASC"))))
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

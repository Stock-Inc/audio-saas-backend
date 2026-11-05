package backend.app.api.backend.audio.controller.controller;

import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import backend.app.api.backend.audio.controller.dto.AudioUploadRequest;
import backend.app.api.backend.audio.controller.dto.AudioUploadResponse;
import backend.app.api.backend.audio.controller.entity.AudioStatus;
import backend.app.api.backend.audio.controller.service.AudioService;
import backend.app.api.backend.config.SecurityConfig;
import io.swagger.v3.core.converter.ModelConverters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AudioController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "KEYCLOAK_CLIENT_SECRET=test-client-secret")
class AudioControllerTests {

    private static final UUID REQUEST_ID = UUID.fromString(
            "3e983854-c2ba-4d12-af8b-a8e2086c1370"
    );
    private static final UUID RECORD_ID = UUID.fromString(
            "a96725b4-df5e-41bb-92c3-3ec3786f8621"
    );
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AudioService audioService;

    @Test
    void uploadReturnsAcceptedResponseForAuthenticatedUser() throws Exception {
        UUID uploadId = UUID.fromString(
                "7747f2c4-c6c0-4b48-9e80-724bb8a85938"
        );
        var response = new AudioUploadResponse(
                uploadId,
                REQUEST_ID,
                RECORD_ID,
                AudioStatus.FORWARDED
        );
        when(audioService.upload(
                any(AudioProcessingMetadata.class),
                any(MultipartFile.class)
        )).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/audio")
                        .file(audioFile())
                        .file(optionsPart("""
                                {"diarization":true,"summary":true,"tasks":true}
                                """))
                        .param("requestId", REQUEST_ID.toString())
                        .param("recordId", RECORD_ID.toString())
                        .with(oidcLogin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(uploadId.toString()))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.recordId").value(RECORD_ID.toString()))
                .andExpect(jsonPath("$.status").value("FORWARDED"));

        var metadataCaptor = org.mockito.ArgumentCaptor.forClass(
                AudioProcessingMetadata.class
        );
        var fileCaptor = org.mockito.ArgumentCaptor.forClass(
                MultipartFile.class
        );
        verify(audioService).upload(
                metadataCaptor.capture(),
                fileCaptor.capture()
        );

        assertEquals(REQUEST_ID, metadataCaptor.getValue().requestId());
        assertEquals(RECORD_ID, metadataCaptor.getValue().recordId());
        assertTrue(metadataCaptor.getValue().options()
                .get("diarization").asBoolean());
        assertTrue(metadataCaptor.getValue().options()
                .get("summary").asBoolean());
        assertTrue(metadataCaptor.getValue().options()
                .get("tasks").asBoolean());
        assertEquals("voice.mp3", fileCaptor.getValue().getOriginalFilename());
    }

    @Test
    void uploadWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/v1/audio")
                        .file(audioFile())
                        .file(optionsPart("{}"))
                        .param("requestId", REQUEST_ID.toString())
                        .param("recordId", RECORD_ID.toString()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(audioService);
    }

    @Test
    void uploadWithMalformedOptionsReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/audio")
                        .file(audioFile())
                        .file(optionsPart("not-json"))
                        .param("requestId", REQUEST_ID.toString())
                        .param("recordId", RECORD_ID.toString())
                        .with(oidcLogin()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(audioService);
    }

    @Test
    void uploadWithoutFileReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/audio")
                        .file(optionsPart("{}"))
                        .param("requestId", REQUEST_ID.toString())
                        .param("recordId", RECORD_ID.toString())
                        .with(oidcLogin()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(audioService);
    }

    @Test
    void uploadSchemaContainsOnlyCurrentMultipartFields() {
        var schema = ModelConverters.getInstance()
                .read(AudioUploadRequest.class)
                .get("AudioUploadRequest");

        assertEquals(
                Set.of("requestId", "recordId", "file", "options"),
                schema.getProperties().keySet()
        );
        assertEquals(
                Set.of("requestId", "recordId", "file", "options"),
                Set.copyOf(schema.getRequired())
        );
    }

    private MockMultipartFile audioFile() {
        return new MockMultipartFile(
                "file",
                "voice.mp3",
                "audio/mpeg",
                "audio-content".getBytes(StandardCharsets.UTF_8)
        );
    }

    private MockMultipartFile optionsPart(String json) {
        return new MockMultipartFile(
                "options",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)
        );
    }
}

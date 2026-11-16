package backend.app.api.backend.audio.controller.service;

import backend.app.api.backend.audio.controller.client.AudioProcessorClient;
import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import backend.app.api.backend.audio.controller.dto.AudioProcessorJobResponse;
import backend.app.api.backend.audio.controller.entity.AudioStatus;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import backend.app.api.backend.audio.controller.repository.AudioUploadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioServiceTests {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String OWNER_ID = "user-123";
    private static final java.util.UUID JOB_ID = java.util.UUID.fromString(
            "7747f2c4-c6c0-4b48-9e80-724bb8a85938"
    );

    @Mock
    private AudioUploadRepository repository;

    @Mock
    private AudioProcessorClient processorClient;

    @InjectMocks
    private AudioService audioService;

    @Test
    void uploadStoresMetadataAndReturnsForwardedStatus() throws Exception {
        returnSavedEntity();
        returnProcessorJob();
        var metadata = metadata("""
                {"diarization":true,"summary":true,"tasks":true}
                """);
        var file = new MockMultipartFile(
                "file",
                "C:\\fakepath\\voice.mp3",
                "audio/mpeg",
                "audio-content".getBytes(StandardCharsets.UTF_8)
        );

        var response = audioService.upload(metadata, file, OWNER_ID);

        var audioCaptor = ArgumentCaptor.forClass(AudioUpload.class);
        verify(repository, times(2)).save(audioCaptor.capture());
        AudioUpload savedAudio = audioCaptor.getValue();

        assertNotNull(response.id());
        assertEquals(response.id(), savedAudio.getId());
        assertEquals("voice.mp3", savedAudio.getOriginalFilename());
        assertEquals("audio/mpeg", savedAudio.getContentType());
        assertEquals(file.getSize(), savedAudio.getSizeBytes());
        assertEquals(OWNER_ID, savedAudio.getOwnerId());
        assertNotNull(savedAudio.getRequestId());
        assertNotNull(savedAudio.getRecordId());
        assertEquals(JOB_ID, savedAudio.getProcessingJobId());
        assertEquals(AudioStatus.FORWARDED, savedAudio.getStatus());
        assertEquals(AudioStatus.FORWARDED, response.status());
        verify(processorClient).forward(
                metadata,
                file,
                "voice.mp3",
                MediaType.parseMediaType("audio/mpeg"),
                savedAudio.getRequestId(),
                savedAudio.getRecordId()
        );
    }

    @Test
    void uploadUsesOctetStreamWhenContentTypeIsMissing() throws Exception {
        returnSavedEntity();
        returnProcessorJob();
        var file = new MockMultipartFile(
                "file",
                "voice.raw",
                null,
                "audio-content".getBytes(StandardCharsets.UTF_8)
        );

        audioService.upload(metadata("{}"), file, OWNER_ID);

        var audioCaptor = ArgumentCaptor.forClass(AudioUpload.class);
        verify(repository, times(2)).save(audioCaptor.capture());
        assertEquals(
                "application/octet-stream",
                audioCaptor.getValue().getContentType()
        );
    }

    @Test
    void uploadRejectsEmptyFile() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "voice.mp3",
                "audio/mpeg",
                new byte[0]
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> audioService.upload(metadata("{}"), file, OWNER_ID)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("File is empty", exception.getReason());
        verify(repository, never()).save(any());
    }

    @Test
    void uploadRejectsOptionsThatAreNotJsonObject() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "voice.mp3",
                "audio/mpeg",
                "audio-content".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> audioService.upload(metadata("[]"), file, OWNER_ID)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Options must be a JSON object", exception.getReason());
        verify(repository, never()).save(any());
    }

    @Test
    void uploadRejectsFilenameLongerThanDatabaseColumn() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "a".repeat(252) + ".mp3",
                "audio/mpeg",
                "audio-content".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> audioService.upload(metadata("{}"), file, OWNER_ID)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Invalid filename", exception.getReason());
        verify(repository, never()).save(any());
    }

    @Test
    void uploadRejectsInvalidContentType() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "voice.mp3",
                "audio/[",
                "audio-content".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> audioService.upload(metadata("{}"), file, OWNER_ID)
        );

        assertEquals(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                exception.getStatusCode()
        );
        assertEquals("Invalid content type", exception.getReason());
        verify(repository, never()).save(any());
    }

    @Test
    void uploadMarksRecordFailedWhenProcessorIsUnavailable()
            throws Exception {
        returnSavedEntity();
        var metadata = metadata("{}");
        var file = new MockMultipartFile(
                "file",
                "voice.mp3",
                "audio/mpeg",
                "audio-content".getBytes(StandardCharsets.UTF_8)
        );
        doThrow(new RestClientException("Processor unavailable"))
                .when(processorClient)
                .forward(
                        any(AudioProcessingMetadata.class),
                        any(MultipartFile.class),
                        any(String.class),
                        any(MediaType.class),
                        any(java.util.UUID.class),
                        any(java.util.UUID.class)
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> audioService.upload(metadata, file, OWNER_ID)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("Audio processor is unavailable", exception.getReason());

        var audioCaptor = ArgumentCaptor.forClass(AudioUpload.class);
        verify(repository, times(2)).save(audioCaptor.capture());
        assertEquals(AudioStatus.FAILED, audioCaptor.getValue().getStatus());
        assertEquals(
                "Failed to forward audio",
                audioCaptor.getValue().getErrorMessage()
        );
    }

    private AudioProcessingMetadata metadata(String json) throws Exception {
        return new AudioProcessingMetadata(
                JSON_MAPPER.readTree(json)
        );
    }

    private void returnSavedEntity() {
        when(repository.save(any(AudioUpload.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void returnProcessorJob() {
        when(processorClient.forward(
                any(AudioProcessingMetadata.class),
                any(MultipartFile.class),
                any(String.class),
                any(MediaType.class),
                any(java.util.UUID.class),
                any(java.util.UUID.class)
        )).thenAnswer(invocation -> new AudioProcessorJobResponse(
                JOB_ID,
                invocation.getArgument(5),
                "QUEUED"
        ));
    }
}

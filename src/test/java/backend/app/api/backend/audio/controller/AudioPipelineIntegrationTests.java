package backend.app.api.backend.audio.controller;

import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import backend.app.api.backend.audio.controller.dto.AudioUploadResponse;
import backend.app.api.backend.audio.controller.entity.AudioStatus;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import backend.app.api.backend.audio.controller.repository.AudioUploadRepository;
import backend.app.api.backend.audio.controller.service.AudioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.docker.compose.enabled=false"
)
@EnabledIfEnvironmentVariable(
        named = "RUN_EXTERNAL_INTEGRATION_TESTS",
        matches = "true"
)
class AudioPipelineIntegrationTests {

    @Autowired
    private AudioService audioService;

    @Autowired
    private AudioUploadRepository repository;

    @Autowired
    private JsonMapper jsonMapper;

    private UUID createdUploadId;

    @AfterEach
    void cleanUp() {
        if (createdUploadId != null) {
            repository.deleteById(createdUploadId);
        }
    }

    @Test
    void uploadIsProcessedAndReturnedThroughKafka() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "integration-test.wav",
                "audio/wav",
                "local integration test audio"
                        .getBytes(StandardCharsets.UTF_8)
        );
        var metadata = new AudioProcessingMetadata(
                jsonMapper.readTree("""
                        {
                          "diarization": true,
                          "summary": true,
                          "tasks": true
                        }
                        """)
        );

        AudioUploadResponse accepted = audioService.upload(
                metadata,
                file,
                "integration-test-" + UUID.randomUUID()
        );
        createdUploadId = accepted.id();

        assertTrue(
                EnumSet.of(AudioStatus.FORWARDED, AudioStatus.COMPLETED)
                        .contains(accepted.status())
        );

        AudioUpload completed = awaitTerminalState(
                accepted.id(),
                Duration.ofSeconds(30)
        );

        assertEquals(AudioStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getProcessingJobId());
        assertEquals("integration-test-result.json", completed.getResultFilename());
        assertEquals("application/json", completed.getResultContentType());
        assertNotNull(completed.getResultData());

        var result = jsonMapper.readTree(completed.getResultData());
        assertEquals(
                completed.getProcessingJobId().toString(),
                result.get("jobId").asText()
        );
        assertEquals(
                completed.getRecordId().toString(),
                result.get("recordId").asText()
        );
        assertEquals("COMPLETED", result.get("status").asText());
    }

    private AudioUpload awaitTerminalState(UUID uploadId, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            AudioUpload current = repository.findById(uploadId).orElseThrow();

            if (current.getStatus() == AudioStatus.COMPLETED) {
                return current;
            }

            if (current.getStatus() == AudioStatus.FAILED) {
                fail("Audio pipeline failed: " + current.getErrorMessage());
            }

            Thread.sleep(250);
        }

        return fail("Audio pipeline did not complete within " + timeout);
    }
}

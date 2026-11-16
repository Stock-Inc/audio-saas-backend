package backend.app.api.backend.audio.controller.kafka;

import backend.app.api.backend.audio.controller.entity.AudioStatus;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import backend.app.api.backend.audio.controller.repository.AudioUploadRepository;
import com.example.ml.infrastructure.kafka.dto.Analysis;
import com.example.ml.infrastructure.kafka.dto.MlJobResultEvent;
import com.example.ml.infrastructure.kafka.dto.MlJobStatus;
import com.example.ml.infrastructure.kafka.dto.Segment;
import com.example.ml.infrastructure.kafka.dto.Transcript;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioResultListenerTests {

    private static final UUID RECORD_ID = UUID.fromString(
            "a96725b4-df5e-41bb-92c3-3ec3786f8621"
    );
    private static final UUID JOB_ID = UUID.fromString(
            "7747f2c4-c6c0-4b48-9e80-724bb8a85938"
    );

    @Test
    void completedEventIsStoredAsDownloadableJsonFile() {
        AudioUploadRepository repository = mock(AudioUploadRepository.class);
        AudioUpload audio = audio();
        var listener = new AudioResultListener(
                repository,
                JsonMapper.builder().build()
        );
        when(repository.findByRecordId(RECORD_ID))
                .thenReturn(Optional.of(audio));

        listener.consume(completedEvent());

        assertEquals(AudioStatus.COMPLETED, audio.getStatus());
        assertEquals(JOB_ID, audio.getProcessingJobId());
        assertEquals("meeting-result.json", audio.getResultFilename());
        assertEquals("application/json", audio.getResultContentType());
        assertEquals(
                audio.getResultData().length,
                audio.getResultSizeBytes()
        );

        String json = new String(
                audio.getResultData(),
                StandardCharsets.UTF_8
        );
        assertTrue(json.contains("Speaker 1"));
        assertTrue(json.contains("Project summary"));
        assertTrue(json.contains(JOB_ID.toString()));
        verify(repository).save(audio);
    }

    private AudioUpload audio() {
        return AudioUpload.builder()
                .id(UUID.randomUUID())
                .requestId(UUID.randomUUID())
                .recordId(RECORD_ID)
                .ownerId("user-123")
                .originalFilename("meeting.wav")
                .contentType("audio/wav")
                .sizeBytes(42L)
                .build();
    }

    private MlJobResultEvent completedEvent() {
        Segment segment = Segment.newBuilder()
                .setId(1)
                .setSpeaker("Speaker 1")
                .setStart(0.0)
                .setEnd(2.5)
                .setText("Hello")
                .build();
        Transcript transcript = Transcript.newBuilder()
                .setSegments(List.of(segment))
                .build();
        Analysis analysis = Analysis.newBuilder()
                .setSummary("Project summary")
                .setTopics(List.of("planning"))
                .setKeyPoints(List.of("key point"))
                .setDecisions(List.of("decision"))
                .setTasks(List.of())
                .build();

        return MlJobResultEvent.newBuilder()
                .setJobId(JOB_ID.toString())
                .setRecordId(RECORD_ID.toString())
                .setStatus(MlJobStatus.COMPLETED)
                .setTranscript(transcript)
                .setAnalysis(analysis)
                .build();
    }
}

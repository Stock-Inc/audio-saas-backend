package backend.app.api.backend.audio.controller.kafka;

import backend.app.api.backend.audio.controller.entity.AudioUpload;
import backend.app.api.backend.audio.controller.repository.AudioUploadRepository;
import com.example.ml.infrastructure.kafka.dto.Analysis;
import com.example.ml.infrastructure.kafka.dto.MlJobResultEvent;
import com.example.ml.infrastructure.kafka.dto.MlJobStatus;
import com.example.ml.infrastructure.kafka.dto.Segment;
import com.example.ml.infrastructure.kafka.dto.Task;
import com.example.ml.infrastructure.kafka.dto.Transcript;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioResultListener {

    private static final String RESULT_CONTENT_TYPE = "application/json";

    private final AudioUploadRepository repository;
    private final JsonMapper jsonMapper;

    @KafkaListener(
            topics = "${audio.kafka.topic.job-results:ml-job-results}",
            groupId = "${audio.kafka.consumer-group:audio-saas-backend}"
    )
    @Transactional
    public void consume(MlJobResultEvent event) {
        UUID recordId = parseUuid(event.getRecordId(), "recordId");
        UUID jobId = parseUuid(event.getJobId(), "jobId");

        AudioUpload audio = repository.findByRecordId(recordId)
                .orElse(null);

        if (audio == null) {
            log.warn(
                    "Ignoring ML result for unknown record: recordId={}, jobId={}",
                    recordId,
                    jobId
            );
            return;
        }

        if (audio.getProcessingJobId() != null
                && !audio.getProcessingJobId().equals(jobId)) {
            log.warn(
                    "Ignoring ML result with unexpected job: uploadId={}, expectedJobId={}, actualJobId={}",
                    audio.getId(),
                    audio.getProcessingJobId(),
                    jobId
            );
            return;
        }

        MlJobStatus status = event.getStatus();

        if (status == MlJobStatus.COMPLETED) {
            byte[] result = serialize(event, jobId, recordId);
            audio.complete(
                    jobId,
                    resultFilename(audio.getOriginalFilename()),
                    RESULT_CONTENT_TYPE,
                    result
            );
        } else if (status == MlJobStatus.FAILED) {
            audio.markFailed("Audio processing failed");
        } else {
            audio.markProcessing(jobId);
        }

        repository.save(audio);

        log.info(
                "ML result applied: uploadId={}, jobId={}, status={}",
                audio.getId(),
                jobId,
                status
        );
    }

    private byte[] serialize(
            MlJobResultEvent event,
            UUID jobId,
            UUID recordId
    ) {
        AudioResultDocument document = new AudioResultDocument(
                jobId,
                recordId,
                event.getStatus().name(),
                mapTranscript(event.getTranscript()),
                mapAnalysis(event.getAnalysis())
        );

        try {
            return jsonMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(document);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to serialize ML result for record " + recordId,
                    exception
            );
        }
    }

    private AudioResultDocument.TranscriptDocument mapTranscript(
            Transcript transcript
    ) {
        if (transcript == null) {
            return null;
        }

        List<AudioResultDocument.SegmentDocument> segments =
                transcript.getSegments().stream()
                        .map(this::mapSegment)
                        .toList();

        return new AudioResultDocument.TranscriptDocument(segments);
    }

    private AudioResultDocument.SegmentDocument mapSegment(Segment segment) {
        return new AudioResultDocument.SegmentDocument(
                segment.getId(),
                asString(segment.getSpeaker()),
                segment.getStart(),
                segment.getEnd(),
                asString(segment.getText())
        );
    }

    private AudioResultDocument.AnalysisDocument mapAnalysis(
            Analysis analysis
    ) {
        if (analysis == null) {
            return null;
        }

        return new AudioResultDocument.AnalysisDocument(
                asString(analysis.getSummary()),
                strings(analysis.getTopics()),
                strings(analysis.getKeyPoints()),
                strings(analysis.getDecisions()),
                analysis.getTasks().stream()
                        .map(this::mapTask)
                        .toList()
        );
    }

    private AudioResultDocument.TaskDocument mapTask(Task task) {
        return new AudioResultDocument.TaskDocument(
                asString(task.getText()),
                asString(task.getAssignee())
        );
    }

    private List<String> strings(List<? extends CharSequence> values) {
        return values.stream().map(this::asString).toList();
    }

    private String asString(CharSequence value) {
        return value == null ? null : value.toString();
    }

    private UUID parseUuid(CharSequence value, String field) {
        try {
            return UUID.fromString(value.toString());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "ML result contains invalid " + field,
                    exception
            );
        }
    }

    private String resultFilename(String originalFilename) {
        int extension = originalFilename.lastIndexOf('.');
        String base = extension > 0
                ? originalFilename.substring(0, extension)
                : originalFilename;
        int maxBaseLength = 255 - "-result.json".length();

        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength);
        }

        return base + "-result.json";
    }
}

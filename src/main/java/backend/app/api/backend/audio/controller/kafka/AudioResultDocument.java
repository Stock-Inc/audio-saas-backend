package backend.app.api.backend.audio.controller.kafka;

import java.util.List;
import java.util.UUID;

record AudioResultDocument(
        UUID jobId,
        UUID recordId,
        String status,
        TranscriptDocument transcript,
        AnalysisDocument analysis
) {
    record TranscriptDocument(List<SegmentDocument> segments) {
    }

    record SegmentDocument(
            Integer id,
            String speaker,
            double start,
            double end,
            String text
    ) {
    }

    record AnalysisDocument(
            String summary,
            List<String> topics,
            List<String> keyPoints,
            List<String> decisions,
            List<TaskDocument> tasks
    ) {
    }

    record TaskDocument(String text, String assignee) {
    }
}

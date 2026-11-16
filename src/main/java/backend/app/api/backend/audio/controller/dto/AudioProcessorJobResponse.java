package backend.app.api.backend.audio.controller.dto;

import java.util.UUID;

public record AudioProcessorJobResponse(
        UUID jobId,
        UUID recordId,
        String status
) {
}

package backend.app.api.backend.audio.controller.dto;

import tools.jackson.databind.JsonNode;

public record AudioProcessingMetadata(
        JsonNode options
) {
}

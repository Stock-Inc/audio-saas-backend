package backend.app.api.backend.audio.controller.dto;

import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record AudioProcessingMetadata(
        UUID requestId,
        UUID recordId,
        JsonNode options
) {}
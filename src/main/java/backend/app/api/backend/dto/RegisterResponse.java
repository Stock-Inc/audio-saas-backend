package backend.app.api.backend.dto;

public record RegisterResponse(
        boolean success,
        String message
) {}
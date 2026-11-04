package backend.app.api.backend.audio.controller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "audio.processor")
public record AudioProcessorProperties(
        URI url,
        Duration connectTimeout,
        Duration readTimeout
) {}
package backend.app.api.backend.audio.controller.client;


import backend.app.api.backend.audio.controller.config.AudioProcessorProperties;
import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import backend.app.api.backend.audio.controller.dto.AudioProcessorJobResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioProcessorClient {

    private final RestClient audioProcessorRestClient;
    private final AudioProcessorProperties properties;

    @Retry(name = "audioProcessor")
    @CircuitBreaker(name = "audioProcessor")
    public AudioProcessorJobResponse forward(
            AudioProcessingMetadata metadata,
            MultipartFile file,
            String filename,
            MediaType mediaType,
            UUID requestId,
            UUID recordId
    ) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();

        body.part("file", file.getResource())
                .filename(filename)
                .contentType(mediaType);

        body.part("options", metadata.options())
                .contentType(MediaType.APPLICATION_JSON);

        log.debug("Forwarding audio to processor");

        var requestUri = UriComponentsBuilder
                .fromUri(properties.url())
                .queryParam("requestId", requestId)
                .queryParam("recordId", recordId)
                .build()
                .encode()
                .toUri();

        var response = audioProcessorRestClient.post()
                .uri(requestUri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .toEntity(AudioProcessorJobResponse.class);

        AudioProcessorJobResponse job = response.getBody();

        if (job == null || job.jobId() == null || job.recordId() == null) {
            throw new org.springframework.web.client.RestClientException(
                    "Audio processor response does not contain required IDs"
            );
        }

        if (!recordId.equals(job.recordId())) {
            throw new org.springframework.web.client.RestClientException(
                    "Audio processor response contains an unexpected recordId"
            );
        }

        log.debug(
                "Audio processor responded: status={}",
                response.getStatusCode()
        );

        return job;
    }
}

package backend.app.api.backend.audio.controller.client;


import backend.app.api.backend.audio.controller.config.AudioProcessorProperties;
import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioProcessorClient {

    private final RestClient audioProcessorRestClient;
    private final AudioProcessorProperties properties;

    @Retry(name = "audioProcessor")
    public void forward(
            AudioProcessingMetadata metadata,
            MultipartFile file,
            String filename,
            MediaType mediaType
    ) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();

        body.part("file", file.getResource())
                .filename(filename)
                .contentType(mediaType);

        body.part("options", metadata.options())
                .contentType(MediaType.APPLICATION_JSON);

        log.debug("Forwarding audio to processor");

        var response = audioProcessorRestClient.post()
                .uri(properties.url())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .toBodilessEntity();

        log.debug(
                "Audio processor responded: status={}",
                response.getStatusCode()
        );
    }
}

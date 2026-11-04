package backend.app.api.backend.audio.controller.client;


import backend.app.api.backend.audio.controller.config.AudioProcessorProperties;
import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class AudioProcessorClient {

    private final RestClient audioProcessorRestClient;
    private final AudioProcessorProperties properties;

    public void forward(
            AudioProcessingMetadata metadata,
            MultipartFile file,
            String filename,
            MediaType mediaType
    ) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();

        body.part("requestId", metadata.requestId().toString());

        body.part("recordId", metadata.recordId().toString());

        body.part("file", file.getResource())
                .filename(filename)
                .contentType(mediaType);

        body.part("options", metadata.options())
                .contentType(MediaType.APPLICATION_JSON);

        body.part("callbackUrl", metadata.callbackUrl().toString());

        audioProcessorRestClient.post()
                .uri(properties.url())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .toBodilessEntity();
    }
}

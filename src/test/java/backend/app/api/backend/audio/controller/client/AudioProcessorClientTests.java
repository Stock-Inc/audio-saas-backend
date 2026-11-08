package backend.app.api.backend.audio.controller.client;

import backend.app.api.backend.audio.controller.config.AudioProcessorProperties;
import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

class AudioProcessorClientTests {

    @Test
    void forwardIncludesFileAndOptionsInMultipartRequest() throws Exception {
        URI processorUrl = URI.create("http://audio-processor/process");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(restClientBuilder)
                .build();
        var properties = new AudioProcessorProperties(
                processorUrl,
                Duration.ofSeconds(5),
                Duration.ofSeconds(120)
        );
        var client = new AudioProcessorClient(
                restClientBuilder.build(),
                properties
        );
        var metadata = new AudioProcessingMetadata(
                JsonMapper.builder().build().readTree("""
                        {"diarization":true,"summary":true,"tasks":true}
                        """)
        );
        var file = new MockMultipartFile(
                "file",
                "voice.mp3",
                "audio/mpeg",
                "audio-content".getBytes(StandardCharsets.UTF_8)
        );

        server.expect(requestTo(processorUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.MULTIPART_FORM_DATA
                ))
                .andExpect(content().string(containsString(
                        "name=\"options\""
                )))
                .andExpect(content().string(containsString(
                        "name=\"file\""
                )))
                .andExpect(content().string(not(containsString(
                        "name=\"requestId\""
                ))))
                .andExpect(content().string(not(containsString(
                        "name=\"recordId\""
                ))))
                .andRespond(withNoContent());

        client.forward(
                metadata,
                file,
                file.getOriginalFilename(),
                MediaType.parseMediaType(file.getContentType())
        );

        server.verify();
    }
}

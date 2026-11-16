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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AudioProcessorClientTests {

    private static final UUID REQUEST_ID = UUID.fromString(
            "3e983854-c2ba-4d12-af8b-a8e2086c1370"
    );
    private static final UUID RECORD_ID = UUID.fromString(
            "a96725b4-df5e-41bb-92c3-3ec3786f8621"
    );

    @Test
    void forwardFollowsPublishedProcessorContract() throws Exception {
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

        URI expectedUrl = URI.create(
                processorUrl
                        + "?requestId=" + REQUEST_ID
                        + "&recordId=" + RECORD_ID
        );
        UUID jobId = UUID.fromString(
                "7747f2c4-c6c0-4b48-9e80-724bb8a85938"
        );

        server.expect(requestTo(expectedUrl))
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
                .andRespond(withSuccess(
                        "{\"jobId\":\"" + jobId
                                + "\",\"recordId\":\"" + RECORD_ID
                                + "\",\"status\":\"QUEUED\"}",
                        MediaType.APPLICATION_JSON
                ));

        var response = client.forward(
                metadata,
                file,
                file.getOriginalFilename(),
                MediaType.parseMediaType(file.getContentType()),
                REQUEST_ID,
                RECORD_ID
        );

        server.verify();
        assertEquals(jobId, response.jobId());
        assertEquals(RECORD_ID, response.recordId());
        assertEquals("QUEUED", response.status());
    }

    @Test
    void forwardRejectsResponseForAnotherRecord() throws Exception {
        URI processorUrl = URI.create("http://audio-processor/process");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(restClientBuilder)
                .build();
        var client = new AudioProcessorClient(
                restClientBuilder.build(),
                new AudioProcessorProperties(
                        processorUrl,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(120)
                )
        );
        var metadata = new AudioProcessingMetadata(
                JsonMapper.builder().build().readTree("{}")
        );
        var file = new MockMultipartFile(
                "file",
                "voice.mp3",
                "audio/mpeg",
                "audio-content".getBytes(StandardCharsets.UTF_8)
        );

        server.expect(requestTo(containsString("recordId=" + RECORD_ID)))
                .andRespond(withSuccess(
                        "{\"jobId\":\"7747f2c4-c6c0-4b48-9e80-724bb8a85938\","
                                + "\"recordId\":\"55555555-5555-5555-5555-555555555555\","
                                + "\"status\":\"QUEUED\"}",
                        MediaType.APPLICATION_JSON
                ));

        var exception = assertThrows(
                org.springframework.web.client.RestClientException.class,
                () -> client.forward(
                        metadata,
                        file,
                        file.getOriginalFilename(),
                        MediaType.parseMediaType(file.getContentType()),
                        REQUEST_ID,
                        RECORD_ID
                )
        );

        assertEquals(
                "Audio processor response contains an unexpected recordId",
                exception.getMessage()
        );
        server.verify();
    }
}

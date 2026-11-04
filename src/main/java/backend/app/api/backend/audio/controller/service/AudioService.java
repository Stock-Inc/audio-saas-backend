package backend.app.api.backend.audio.controller.service;

import backend.app.api.backend.audio.controller.client.AudioProcessorClient;
import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import backend.app.api.backend.audio.controller.dto.AudioUploadResponse;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import backend.app.api.backend.audio.controller.repository.AudioUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioService {

    private final AudioUploadRepository repository;
    private final AudioProcessorClient processorClient;

    public AudioUploadResponse upload(
            AudioProcessingMetadata metadata,
            MultipartFile file
    ) {
        ValidatedFile validated = validate(metadata, file);

        AudioUpload audio = saveUpload(metadata, file, validated);

        forwardAudio(audio, metadata, file, validated);

        return AudioUploadResponse.from(audio);
    }

    private ValidatedFile validate(
            AudioProcessingMetadata metadata,
            MultipartFile file
    ) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File is empty"
            );
        }

        if (metadata.options() == null ||
                !metadata.options().isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Options must be a JSON object"
            );
        }

        validateCallbackUrl(metadata.callbackUrl());

        String filename = file.getOriginalFilename();

        if (filename == null || filename.isBlank()) {
            filename = "file";
        }

        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);

        if (filename.isBlank()
                || filename.length() > 255
                || filename.chars().anyMatch(c -> c < 32)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid filename"
            );
        }

        MediaType mediaType = parseMediaType(file.getContentType());

        return new ValidatedFile(filename, mediaType);
    }

    private void validateCallbackUrl(URI callbackUrl) {
        if (callbackUrl == null
                || !callbackUrl.isAbsolute()
                || callbackUrl.getHost() == null
                || !("http".equalsIgnoreCase(callbackUrl.getScheme())
                || "https".equalsIgnoreCase(callbackUrl.getScheme()))
                || callbackUrl.toString().length() > 2048) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Callback URL must be an absolute HTTP or HTTPS URL"
            );
        }
    }

    private MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);

            if (mediaType.toString().length() > 100) {
                throw new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Content type is too long"
                );
            }

            return mediaType;
        } catch (InvalidMediaTypeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Invalid content type",
                    exception
            );
        }
    }

    private AudioUpload saveUpload(
            AudioProcessingMetadata metadata,
            MultipartFile file,
            ValidatedFile validated
    ) {
        AudioUpload audio = AudioUpload.builder()
                .id(UUID.randomUUID())
                .requestId(metadata.requestId())
                .recordId(metadata.recordId())
                .callbackUrl(metadata.callbackUrl().toString())
                .originalFilename(validated.filename())
                .contentType(validated.mediaType().toString())
                .sizeBytes(file.getSize())
                .build();

        return repository.save(audio);
    }

    private void forwardAudio(
            AudioUpload audio,
            AudioProcessingMetadata metadata,
            MultipartFile file,
            ValidatedFile validated
    ) {
        try {
            processorClient.forward(
                    metadata,
                    file,
                    validated.filename(),
                    validated.mediaType()
            );

            audio.markForwarded();
            repository.save(audio);

        } catch (RestClientException exception) {
            log.error(
                    "Audio forwarding failed: {}",
                    audio.getId(),
                    exception
            );

            audio.markFailed("Failed to forward audio");
            repository.save(audio);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Audio processor is unavailable",
                    exception
            );
        }
    }

    private record ValidatedFile(
            String filename,
            MediaType mediaType
    ) {}
}

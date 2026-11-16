package backend.app.api.backend.audio.controller.service;

import backend.app.api.backend.audio.controller.client.AudioProcessorClient;
import backend.app.api.backend.audio.controller.dto.AudioProcessingMetadata;
import backend.app.api.backend.audio.controller.dto.AudioProcessorJobResponse;
import backend.app.api.backend.audio.controller.dto.AudioUploadResponse;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import backend.app.api.backend.audio.controller.repository.AudioUploadRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioService {

    private final AudioUploadRepository repository;
    private final AudioProcessorClient processorClient;
    private final TransactionTemplate transactionTemplate;

    public AudioUploadResponse upload(
            AudioProcessingMetadata metadata,
            MultipartFile file,
            String ownerId
    ) {
        log.info(
                "Audio upload started: sizeBytes={}, contentType={}",
                file.getSize(),
                file.getContentType()
        );

        ValidatedFile validated;

        try {
            validated = validate(metadata, file);
        } catch (ResponseStatusException exception) {
            log.warn(
                    "Audio upload rejected: status={}, reason={}",
                    exception.getStatusCode().value(),
                    exception.getReason()
            );
            throw exception;
        }

        AudioUpload audio = saveUpload(file, validated, ownerId);

        log.info(
                "Audio upload stored: uploadId={}",
                audio.getId()
        );

        audio = forwardAudio(audio, metadata, file, validated);

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
            MultipartFile file,
            ValidatedFile validated,
            String ownerId
    ) {
        AudioUpload audio = AudioUpload.builder()
                .id(UUID.randomUUID())
                .requestId(UUID.randomUUID())
                .recordId(UUID.randomUUID())
                .ownerId(ownerId)
                .originalFilename(validated.filename())
                .contentType(validated.mediaType().toString())
                .sizeBytes(file.getSize())
                .build();

        return repository.save(audio);
    }

    private AudioUpload forwardAudio(
            AudioUpload audio,
            AudioProcessingMetadata metadata,
            MultipartFile file,
            ValidatedFile validated
    ) {
        try {
            AudioProcessorJobResponse job = processorClient.forward(
                    metadata,
                    file,
                    validated.filename(),
                    validated.mediaType(),
                    audio.getRequestId(),
                    audio.getRecordId()
            );

            AudioUpload current = repository.findById(audio.getId())
                    .orElse(audio);
            current.markForwarded(job.jobId());
            repository.save(current);

            log.info(
                    "Audio upload forwarded: uploadId={}",
                    current.getId()
            );

            return current;

        } catch (CallNotPermittedException exception) {
            log.warn(
                    "Audio processor circuit breaker is open: uploadId={}",
                    audio.getId()
            );

            audio.markFailed("Audio processor temporarily unavailable");
            repository.save(audio);

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Audio processor temporarily unavailable",
                    exception
            );

        } catch (RestClientException exception) {
            log.error(
                    "Audio forwarding failed: uploadId={}",
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

package backend.app.api.backend.audio.controller.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audio_uploads")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AudioUpload {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "record_id")
    private UUID recordId;

    @Column(name = "callback_url", length = 2048)
    private String callbackUrl;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AudioStatus status = AudioStatus.RECEIVED;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void markForwarded() {
        status = AudioStatus.FORWARDED;
        errorMessage = null;
    }

    public void markFailed(String message) {
        status = AudioStatus.FAILED;
        errorMessage = message;
    }
}

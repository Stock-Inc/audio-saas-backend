package backend.app.api.backend.audio.controller.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;

    @Column(name = "processing_job_id")
    private UUID processingJobId;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private AudioFolder folder;

    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "audio_upload_tags",
            joinColumns = @JoinColumn(name = "audio_upload_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<AudioTag> tags = new HashSet<>();

    @Column(name = "result_filename", length = 255)
    private String resultFilename;

    @Column(name = "result_content_type", length = 100)
    private String resultContentType;

    @Column(name = "result_size_bytes")
    private Long resultSizeBytes;

    @Column(name = "result_data", columnDefinition = "bytea")
    private byte[] resultData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void markForwarded(UUID jobId) {
        processingJobId = jobId;

        if (status == AudioStatus.RECEIVED) {
            status = AudioStatus.FORWARDED;
            errorMessage = null;
        }
    }

    public void markProcessing(UUID jobId) {
        processingJobId = jobId;

        if (status != AudioStatus.COMPLETED) {
            status = AudioStatus.PROCESSING;
            errorMessage = null;
        }
    }

    public void markFailed(String message) {
        if (status != AudioStatus.COMPLETED) {
            status = AudioStatus.FAILED;
            errorMessage = message;
        }
    }

    public void complete(
            UUID jobId,
            String filename,
            String contentType,
            byte[] data
    ) {
        processingJobId = jobId;
        resultFilename = filename;
        resultContentType = contentType;
        resultData = data.clone();
        resultSizeBytes = (long) data.length;
        status = AudioStatus.COMPLETED;
        errorMessage = null;
    }

    public void moveTo(AudioFolder newFolder) {
        folder = newFolder;
    }

    public void replaceTags(Set<AudioTag> selectedTags) {
        tags.clear();
        tags.addAll(selectedTags);
    }
}

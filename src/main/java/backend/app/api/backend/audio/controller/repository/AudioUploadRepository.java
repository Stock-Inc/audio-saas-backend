package backend.app.api.backend.audio.controller.repository;

import backend.app.api.backend.audio.controller.entity.AudioUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AudioUploadRepository
        extends JpaRepository<AudioUpload, UUID> {

    Optional<AudioUpload> findByIdAndOwnerId(UUID id, String ownerId);

    Optional<AudioUpload> findByRecordId(UUID recordId);

    List<AudioUpload> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<AudioUpload> findAllByOwnerIdAndFolderIdOrderByCreatedAtDesc(
            String ownerId,
            UUID folderId
    );

    List<AudioUpload> findDistinctByOwnerIdAndTagsIdOrderByCreatedAtDesc(
            String ownerId,
            UUID tagId
    );

    List<AudioUpload> findDistinctByOwnerIdAndFolderIdAndTagsIdOrderByCreatedAtDesc(
            String ownerId,
            UUID folderId,
            UUID tagId
    );

    List<AudioUpload> findAllByIdInAndOwnerId(
            Set<UUID> ids,
            String ownerId
    );
}

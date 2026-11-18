package backend.app.api.backend.audio.controller.repository;

import backend.app.api.backend.audio.controller.entity.AudioUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AudioUploadRepository
        extends JpaRepository<AudioUpload, UUID> {

    Optional<AudioUpload> findByIdAndOwnerId(UUID id, String ownerId);

    Optional<AudioUpload> findByRecordId(UUID recordId);

    List<AudioUpload> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);

    @Query(value = """
            SELECT audio.*
            FROM audio_uploads audio
            CROSS JOIN plainto_tsquery('pg_catalog.russian', :query) AS search(term)
            WHERE audio.owner_id = :ownerId
              AND audio.result_search_vector @@ search.term
            ORDER BY ts_rank(audio.result_search_vector, search.term) DESC,
                     audio.created_at DESC, audio.id
            LIMIT 50 OFFSET :offset
            """, nativeQuery = true)
    List<AudioUpload> searchResults(
            @Param("ownerId") String ownerId,
            @Param("query") String query,
            @Param("offset") int offset
    );

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

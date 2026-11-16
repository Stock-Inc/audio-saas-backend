package backend.app.api.backend.audio.controller.repository;

import backend.app.api.backend.audio.controller.entity.AudioFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AudioFolderRepository extends JpaRepository<AudioFolder, UUID> {

    List<AudioFolder> findAllByOwnerIdOrderByNameAsc(String ownerId);

    Optional<AudioFolder> findByIdAndOwnerId(UUID id, String ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(String ownerId, String name);
}

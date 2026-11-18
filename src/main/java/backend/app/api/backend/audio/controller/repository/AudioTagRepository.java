package backend.app.api.backend.audio.controller.repository;

import backend.app.api.backend.audio.controller.entity.AudioTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AudioTagRepository extends JpaRepository<AudioTag, UUID> {

    List<AudioTag> findAllByOwnerIdOrderByNameAsc(String ownerId);

    List<AudioTag> findAllByIdInAndOwnerId(Set<UUID> ids, String ownerId);

    boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(String ownerId, String name, UUID id);

    Optional<AudioTag> findByIdAndOwnerId(UUID id, String ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(String ownerId, String name);
}

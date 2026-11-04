package backend.app.api.backend.audio.controller.repository;

import backend.app.api.backend.audio.controller.entity.AudioUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AudioUploadRepository
        extends JpaRepository<AudioUpload, UUID> {
}
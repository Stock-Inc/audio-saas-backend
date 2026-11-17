package backend.app.api.backend.audio.controller.service;

import backend.app.api.backend.audio.controller.dto.AudioFileResponse;
import backend.app.api.backend.audio.controller.dto.FolderResponse;
import backend.app.api.backend.audio.controller.dto.TagResponse;
import backend.app.api.backend.audio.controller.entity.AudioFolder;
import backend.app.api.backend.audio.controller.entity.AudioTag;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import backend.app.api.backend.audio.controller.repository.AudioFolderRepository;
import backend.app.api.backend.audio.controller.repository.AudioTagRepository;
import backend.app.api.backend.audio.controller.repository.AudioUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AudioLibraryService {

    private final AudioUploadRepository audioRepository;
    private final AudioFolderRepository folderRepository;
    private final AudioTagRepository tagRepository;

    @Transactional
    public FolderResponse createFolder(
            String ownerId,
            String requestedName,
            UUID parentId
    ) {
        String name = requestedName.trim();
        AudioFolder parent = resolveFolder(ownerId, parentId);

        if (folderExists(ownerId, parentId, name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Folder with this name already exists in the parent folder"
            );
        }

        AudioFolder folder = AudioFolder.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .name(name)
                .parent(parent)
                .build();

        return FolderResponse.from(folderRepository.save(folder));
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> folders(String ownerId) {
        return folderRepository.findAllByOwnerIdOrderByNameAsc(ownerId)
                .stream()
                .map(FolderResponse::from)
                .toList();
    }

    @Transactional
    public TagResponse createTag(String ownerId, String requestedName) {
        String name = requestedName.trim();

        if (tagRepository.existsByOwnerIdAndNameIgnoreCase(ownerId, name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tag with this name already exists"
            );
        }

        AudioTag tag = AudioTag.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .name(name)
                .build();

        return TagResponse.from(tagRepository.save(tag));
    }

    @Transactional(readOnly = true)
    public List<TagResponse> tags(String ownerId) {
        return tagRepository.findAllByOwnerIdOrderByNameAsc(ownerId)
                .stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AudioFileResponse> audioFiles(
            String ownerId,
            UUID folderId,
            UUID tagId
    ) {
        List<AudioUpload> files;

        if (folderId != null && tagId != null) {
            files = audioRepository
                    .findDistinctByOwnerIdAndFolderIdAndTagsIdOrderByCreatedAtDesc(
                            ownerId,
                            folderId,
                            tagId
                    );
        } else if (folderId != null) {
            files = audioRepository
                    .findAllByOwnerIdAndFolderIdOrderByCreatedAtDesc(
                            ownerId,
                            folderId
                    );
        } else if (tagId != null) {
            files = audioRepository
                    .findDistinctByOwnerIdAndTagsIdOrderByCreatedAtDesc(
                            ownerId,
                            tagId
                    );
        } else {
            files = audioRepository.findAllByOwnerIdOrderByCreatedAtDesc(
                    ownerId
            );
        }

        return files.stream().map(AudioFileResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AudioFileResponse audioFile(String ownerId, UUID audioId) {
        return AudioFileResponse.from(requireAudio(ownerId, audioId));
    }

    @Transactional
    public AudioFileResponse move(
            String ownerId,
            UUID audioId,
            UUID folderId
    ) {
        AudioUpload audio = requireAudio(ownerId, audioId);
        audio.moveTo(resolveFolder(ownerId, folderId));
        return AudioFileResponse.from(audioRepository.save(audio));
    }

    @Transactional
    public List<AudioFileResponse> moveAll(
            String ownerId,
            Set<UUID> audioIds,
            UUID folderId
    ) {
        List<AudioUpload> files = audioRepository.findAllByIdInAndOwnerId(
                audioIds,
                ownerId
        );

        if (files.size() != audioIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "One or more audio files were not found"
            );
        }

        AudioFolder folder = resolveFolder(ownerId, folderId);
        files.forEach(file -> file.moveTo(folder));

        return audioRepository.saveAll(files).stream()
                .map(AudioFileResponse::from)
                .toList();
    }

    @Transactional
    public AudioFileResponse replaceTags(
            String ownerId,
            UUID audioId,
            Set<UUID> tagIds
    ) {
        AudioUpload audio = requireAudio(ownerId, audioId);
        List<AudioTag> selected = tagIds.isEmpty()
                ? List.of()
                : tagRepository.findAllByIdInAndOwnerId(tagIds, ownerId);

        if (selected.size() != tagIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "One or more tags were not found"
            );
        }

        audio.replaceTags(new HashSet<>(selected));
        return AudioFileResponse.from(audioRepository.save(audio));
    }

    @Transactional(readOnly = true)
    public ResultFile result(String ownerId, UUID audioId) {
        AudioUpload audio = requireAudio(ownerId, audioId);

        if (audio.getResultData() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Audio result is not ready"
            );
        }

        return new ResultFile(
                audio.getResultFilename(),
                audio.getResultContentType(),
                audio.getResultData().clone()
        );
    }

    private AudioUpload requireAudio(String ownerId, UUID audioId) {
        return audioRepository.findByIdAndOwnerId(audioId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Audio file not found"
                ));
    }

    private AudioFolder resolveFolder(String ownerId, UUID folderId) {
        if (folderId == null) {
            return null;
        }

        return folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Folder not found"
                ));
    }

    private boolean folderExists(String ownerId, UUID parentId, String name) {
        if (parentId == null) {
            return folderRepository
                    .existsByOwnerIdAndParentIsNullAndNameIgnoreCase(
                            ownerId,
                            name
                    );
        }

        return folderRepository
                .existsByOwnerIdAndParent_IdAndNameIgnoreCase(
                        ownerId,
                        parentId,
                        name
                );
    }

    public record ResultFile(
            String filename,
            String contentType,
            byte[] data
    ) {
    }
}

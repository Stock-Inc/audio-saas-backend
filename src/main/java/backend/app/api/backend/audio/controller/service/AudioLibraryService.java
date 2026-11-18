package backend.app.api.backend.audio.controller.service;

import backend.app.api.backend.audio.controller.dto.AudioFileResponse;
import backend.app.api.backend.audio.controller.dto.AudioSort;
import backend.app.api.backend.audio.controller.dto.FolderResponse;
import backend.app.api.backend.audio.controller.dto.TagResponse;
import backend.app.api.backend.audio.controller.entity.AudioFolder;
import backend.app.api.backend.audio.controller.entity.AudioTag;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import backend.app.api.backend.audio.controller.repository.AudioFolderRepository;
import backend.app.api.backend.audio.controller.repository.AudioTagRepository;
import backend.app.api.backend.audio.controller.repository.AudioUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
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

    @Transactional
    public FolderResponse renameFolder(String ownerId, UUID id, String requestedName) {
        AudioFolder folder = resolveFolder(ownerId, id);
        String name = requestedName.trim();
        UUID parentId = folder.getParent() == null ? null : folder.getParent().getId();

        if (!folder.getName().equalsIgnoreCase(name) && folderExists(ownerId, parentId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Folder name already exists in this parent");
        }

        folder.rename(name);
        try {
            folderRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Folder name already exists in this parent", ex);
        }
        return FolderResponse.from(folder);
    }

    @Transactional
    public void deleteFolder(String ownerId, UUID id) {
        AudioFolder folder = resolveFolder(ownerId, id);
        folderRepository.delete(folder);
        try {
            folderRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Folder has subfolders", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> folders(String ownerId) {
        return folderRepository.findAllByOwnerIdOrderByNameAsc(ownerId)
                .stream()
                .map(FolderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> childFolders(String ownerId, UUID parentId) {
        if (parentId == null) {
            return folderRepository.findAllByOwnerIdAndParentIsNullOrderByNameAsc(ownerId)
                    .stream()
                    .map(FolderResponse::from)
                    .toList();
        }

        resolveFolder(ownerId, parentId);
        return folderRepository.findAllByOwnerIdAndParent_IdOrderByNameAsc(ownerId, parentId)
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

    @Transactional
    public TagResponse renameTag(String ownerId, UUID id, String requestedName) {
        AudioTag tag = requireTag(ownerId, id);
        String name = requestedName.trim();

        if (!tag.getName().equals(name) && tagRepository.existsByOwnerIdAndNameIgnoreCaseAndIdNot(ownerId, name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag name already exists");
        }

        tag.rename(name);
        try {
            tagRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag name already exists", ex);
        }
        return TagResponse.from(tag);
    }

    @Transactional
    public void deleteTag(String ownerId, UUID id) {
        tagRepository.delete(requireTag(ownerId, id));
    }

    private AudioTag requireTag(String ownerId, UUID id) {
        return tagRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));
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
            UUID tagId,
            boolean filterByFolder,
            AudioSort sort
    ) {
        List<AudioUpload> files;

        if (filterByFolder && tagId != null) {
            files = audioRepository
                    .findDistinctByOwnerIdAndFolderIdAndTagsIdOrderByCreatedAtDesc(
                            ownerId,
                            folderId,
                            tagId
                    );
        } else if (filterByFolder) {
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

        var newest = Comparator.comparing(AudioUpload::getCreatedAt).reversed();
        Comparator<AudioUpload> order = switch (sort) {
            case NEWEST -> newest;
            case OLDEST -> newest.reversed();
            case NAME_ASC -> Comparator.comparing(AudioUpload::getOriginalFilename, String.CASE_INSENSITIVE_ORDER);
            case NAME_DESC -> Comparator.comparing(AudioUpload::getOriginalFilename, String.CASE_INSENSITIVE_ORDER)
                    .reversed();
            case SIZE_ASC -> Comparator.comparingLong(AudioUpload::getSizeBytes);
            case SIZE_DESC -> Comparator.comparingLong(AudioUpload::getSizeBytes).reversed();
        };

        return files.stream()
                .sorted(order.thenComparing(AudioUpload::getId))
                .map(AudioFileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AudioFileResponse> searchAudio(String ownerId, String query, int offset) {
        String text = query.strip();
        if (text.isEmpty() || text.length() > 200 || offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search query or offset");
        }

        return audioRepository.searchResults(ownerId, text, offset)
                .stream()
                .map(AudioFileResponse::from)
                .toList();
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

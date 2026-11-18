package backend.app.api.backend.audio.controller.service;

import backend.app.api.backend.audio.controller.dto.AudioFileResponse;
import backend.app.api.backend.audio.controller.dto.AudioSort;
import backend.app.api.backend.audio.controller.entity.AudioFolder;
import backend.app.api.backend.audio.controller.entity.AudioTag;
import backend.app.api.backend.audio.controller.entity.AudioUpload;
import backend.app.api.backend.audio.controller.repository.AudioFolderRepository;
import backend.app.api.backend.audio.controller.repository.AudioTagRepository;
import backend.app.api.backend.audio.controller.repository.AudioUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioLibraryServiceTests {

    private static final String OWNER_ID = "user-123";

    @Mock
    private AudioUploadRepository audioRepository;

    @Mock
    private AudioFolderRepository folderRepository;

    @Mock
    private AudioTagRepository tagRepository;

    private AudioLibraryService service;

    @BeforeEach
    void setUp() {
        service = new AudioLibraryService(
                audioRepository,
                folderRepository,
                tagRepository
        );
    }

    @Test
    void searchAudioTrimsQueryAndScopesResultsToOwner() {
        when(audioRepository.searchResults(OWNER_ID, "план релиза", 50))
                .thenReturn(List.of());

        assertEquals(List.of(), service.searchAudio(OWNER_ID, "  план релиза  ", 50));
        verify(audioRepository).searchResults(OWNER_ID, "план релиза", 50);
    }

    @Test
    void searchAudioRejectsBlankQueryAndNegativeOffset() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.searchAudio(OWNER_ID, " ", 0)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.searchAudio(OWNER_ID, "план", -1)).getStatusCode());
    }

    @Test
    void childFoldersReturnsEmptyListForOwnedLeaf() {
        AudioFolder parent = folder();
        when(folderRepository.findByIdAndOwnerId(parent.getId(), OWNER_ID))
                .thenReturn(Optional.of(parent));
        when(folderRepository.findAllByOwnerIdAndParent_IdOrderByNameAsc(OWNER_ID, parent.getId()))
                .thenReturn(List.of());

        assertEquals(List.of(), service.childFolders(OWNER_ID, parent.getId()));
    }

    @Test
    void childFoldersRejectsUnavailableParentBeforeQueryingChildren() {
        UUID parentId = UUID.randomUUID();
        when(folderRepository.findByIdAndOwnerId(parentId, OWNER_ID)).thenReturn(Optional.empty());

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.childFolders(OWNER_ID, parentId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(folderRepository, never()).findAllByOwnerIdAndParent_IdOrderByNameAsc(OWNER_ID, parentId);
    }

    @Test
    void moveAllMovesEverySelectedAudioToOwnedFolder() {
        AudioUpload first = audio();
        AudioUpload second = audio();
        AudioFolder folder = folder();
        Set<UUID> ids = Set.of(first.getId(), second.getId());

        when(audioRepository.findAllByIdInAndOwnerId(ids, OWNER_ID))
                .thenReturn(List.of(first, second));
        when(folderRepository.findByIdAndOwnerId(folder.getId(), OWNER_ID))
                .thenReturn(Optional.of(folder));
        when(audioRepository.saveAll(any()))
                .thenReturn(List.of(first, second));

        var result = service.moveAll(OWNER_ID, ids, folder.getId());

        assertEquals(2, result.size());
        assertEquals(folder, first.getFolder());
        assertEquals(folder, second.getFolder());
    }

    @Test
    void createFolderCreatesChildUnderOwnedParent() {
        AudioFolder parent = folder();
        when(folderRepository.findByIdAndOwnerId(parent.getId(), OWNER_ID))
                .thenReturn(Optional.of(parent));
        when(folderRepository.existsByOwnerIdAndParent_IdAndNameIgnoreCase(
                OWNER_ID,
                parent.getId(),
                "Interviews"
        )).thenReturn(false);
        when(folderRepository.save(any(AudioFolder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createFolder(
                OWNER_ID,
                " Interviews ",
                parent.getId()
        );

        assertEquals("Interviews", result.name());
        assertEquals(parent.getId(), result.parentId());
        verify(folderRepository).save(argThat(folder ->
                folder.getParent() == parent
                        && folder.getName().equals("Interviews")
        ));
    }

    @Test
    void createFolderRejectsParentOwnedByAnotherUser() {
        UUID foreignParentId = UUID.randomUUID();
        when(folderRepository.findByIdAndOwnerId(foreignParentId, OWNER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createFolder(
                        OWNER_ID,
                        "Interviews",
                        foreignParentId
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(folderRepository, never()).save(any());
    }

    @Test
    void createFolderRejectsDuplicateNameWithinSameParent() {
        AudioFolder parent = folder();
        when(folderRepository.findByIdAndOwnerId(parent.getId(), OWNER_ID))
                .thenReturn(Optional.of(parent));
        when(folderRepository.existsByOwnerIdAndParent_IdAndNameIgnoreCase(
                OWNER_ID,
                parent.getId(),
                "Interviews"
        )).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createFolder(
                        OWNER_ID,
                        "Interviews",
                        parent.getId()
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(folderRepository, never()).save(any());
    }

    @Test
    void replaceTagsRejectsTagOwnedByAnotherUser() {
        AudioUpload audio = audio();
        UUID foreignTagId = UUID.randomUUID();
        when(audioRepository.findByIdAndOwnerId(audio.getId(), OWNER_ID))
                .thenReturn(Optional.of(audio));
        when(tagRepository.findAllByIdInAndOwnerId(
                Set.of(foreignTagId),
                OWNER_ID
        )).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.replaceTags(
                        OWNER_ID,
                        audio.getId(),
                        Set.of(foreignTagId)
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertNull(audio.getFolder());
        verify(audioRepository, never()).save(any());
    }

    @Test
    void renameFolderKeepsParentAndTrimsName() {
        AudioFolder parent = folder();
        AudioFolder child = AudioFolder.builder().id(UUID.randomUUID())
                .ownerId(OWNER_ID).name("Old").parent(parent).build();
        when(folderRepository.findByIdAndOwnerId(child.getId(), OWNER_ID)).thenReturn(Optional.of(child));

        var response = service.renameFolder(OWNER_ID, child.getId(), " New ");

        assertEquals("New", response.name());
        assertEquals(parent.getId(), response.parentId());
    }

    @Test
    void folderDeletionWithChildrenReturnsConflict() {
        AudioFolder folder = folder();
        when(folderRepository.findByIdAndOwnerId(folder.getId(), OWNER_ID)).thenReturn(Optional.of(folder));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("parent FK")).when(folderRepository).flush();

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.deleteFolder(OWNER_ID, folder.getId()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(folderRepository).delete(folder);
    }

    @Test
    void tagRenameRejectsDuplicateAndDeleteRequiresOwnership() {
        UUID id = UUID.randomUUID();
        AudioTag tag = AudioTag.builder().id(id).ownerId(OWNER_ID).name("Original").build();
        when(tagRepository.findByIdAndOwnerId(id, OWNER_ID)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByOwnerIdAndNameIgnoreCaseAndIdNot(OWNER_ID, "Duplicate", id)).thenReturn(true);

        var conflict = assertThrows(ResponseStatusException.class,
                () -> service.renameTag(OWNER_ID, id, "Duplicate"));
        var missing = assertThrows(ResponseStatusException.class,
                () -> service.deleteTag("other-owner", id));

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("Original", tag.getName());
        assertEquals("Renamed", service.renameTag(OWNER_ID, id, "Renamed").name());
        service.deleteTag(OWNER_ID, id);
        verify(tagRepository).delete(tag);
    }

    @Test
    void audioSortsFilteredResultsByFilenameIgnoringCaseOrBySize() {
        UUID folderId = UUID.randomUUID();
        AudioUpload first = AudioUpload.builder().id(UUID.randomUUID())
                .originalFilename("alpha.wav").sizeBytes(100).build();
        AudioUpload second = AudioUpload.builder().id(UUID.randomUUID())
                .originalFilename("Beta.wav").sizeBytes(50).build();
        when(audioRepository.findAllByOwnerIdAndFolderIdOrderByCreatedAtDesc(OWNER_ID, folderId))
                .thenReturn(List.of(second, first));

        assertEquals(List.of(first.getId(), second.getId()),
                service.audioFiles(OWNER_ID, folderId, null, true, AudioSort.NAME_ASC).stream()
                        .map(AudioFileResponse::id).toList());
        assertEquals(List.of(second.getId(), first.getId()),
                service.audioFiles(OWNER_ID, folderId, null, true, AudioSort.SIZE_ASC).stream()
                        .map(AudioFileResponse::id).toList());
    }

    private AudioUpload audio() {
        return AudioUpload.builder()
                .id(UUID.randomUUID())
                .requestId(UUID.randomUUID())
                .recordId(UUID.randomUUID())
                .ownerId(OWNER_ID)
                .originalFilename("meeting.wav")
                .contentType("audio/wav")
                .sizeBytes(42L)
                .build();
    }

    private AudioFolder folder() {
        return AudioFolder.builder()
                .id(UUID.randomUUID())
                .ownerId(OWNER_ID)
                .name("Meetings")
                .build();
    }
}

package cloudStorage.server.controller;

import cloudStorage.server.model.FolderDto;
import cloudStorage.server.service.FolderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FolderControllerTest {

    @InjectMocks
    private FolderController folderController;

    @Mock
    private FolderService folderService;

    @Mock
    private Principal principal;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createFolder_ShouldReturnCreatedFolder() {
        FolderDto dto = new FolderDto();
        dto.setName("New Folder");

        when(principal.getName()).thenReturn("testuser");
        when(folderService.createFolder(dto, "testuser")).thenReturn(dto);

        ResponseEntity<FolderDto> response = folderController.createFolder(dto, principal);

        assertThat(response.getBody()).isEqualTo(dto);
        verify(folderService, times(1)).createFolder(dto, "testuser");
    }

    @Test
    void listRootFolders_ShouldReturnFolders() {
        FolderDto folder1 = new FolderDto();
        folder1.setName("Folder1");
        FolderDto folder2 = new FolderDto();
        folder2.setName("Folder2");

        when(principal.getName()).thenReturn("testuser");
        when(folderService.listUserRootFolders("testuser")).thenReturn(List.of(folder1, folder2));

        ResponseEntity<List<FolderDto>> response = folderController.listRootFolders(principal);

        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).contains(folder1, folder2);
        verify(folderService, times(1)).listUserRootFolders("testuser");
    }

    @Test
    void listSubfolders_ShouldReturnSubfolders() {
        FolderDto sub1 = new FolderDto();
        sub1.setName("Sub1");

        when(folderService.listSubFolders(1L)).thenReturn(List.of(sub1));

        ResponseEntity<List<FolderDto>> response = folderController.listSubfolders(1L);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getName()).isEqualTo("Sub1");
        verify(folderService, times(1)).listSubFolders(1L);
    }

    @Test
    void deleteFolder_ShouldReturnDeletedMessage() {
        when(principal.getName()).thenReturn("testuser");

        ResponseEntity<String> response = folderController.deleteFolder(1L, principal);

        assertThat(response.getBody()).isEqualTo("Удалено");
        verify(folderService, times(1)).deleteFolder(1L, "testuser");
    }

    @Test
    void renameFolder_ShouldReturnRenamedFolder() {
        FolderDto dto = new FolderDto();
        dto.setName("Renamed");

        when(principal.getName()).thenReturn("testuser");
        when(folderService.renameFolder(1L, "Renamed", "testuser")).thenReturn(dto);

        ResponseEntity<FolderDto> response = folderController.renameFolder(1L, dto, principal);

        assertThat(response.getBody().getName()).isEqualTo("Renamed");
        verify(folderService, times(1)).renameFolder(1L, "Renamed", "testuser");
    }
}

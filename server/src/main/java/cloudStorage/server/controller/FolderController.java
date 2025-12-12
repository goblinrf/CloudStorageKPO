package cloudStorage.server.controller;

import cloudStorage.server.model.FolderDto;
import cloudStorage.server.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping("/create")
    public ResponseEntity<FolderDto> createFolder(@RequestBody FolderDto dto,
                                                  Principal principal) {
        // Проверка родительской папки, если указан parentId
        if (dto.getParentId() != null && !folderService.isOwner(dto.getParentId(), principal.getName())) {
            throw new AccessDeniedException("Нет доступа к родительской папке");
        }
        return ResponseEntity.ok(folderService.createFolder(dto, principal.getName()));
    }

    @GetMapping("/root")
    public ResponseEntity<List<FolderDto>> listRootFolders(Principal principal) {
        return ResponseEntity.ok(folderService.listUserRootFolders(principal.getName()));
    }

    @GetMapping("/{id}/sub")
    public ResponseEntity<List<FolderDto>> listSubfolders(@PathVariable Long id, Principal principal) {
        // Проверка владельца родительской папки
        if (!folderService.isOwner(id, principal.getName())) {
            throw new AccessDeniedException("Нет доступа к папке");
        }
        return ResponseEntity.ok(folderService.listSubFolders(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFolder(@PathVariable Long id, Principal principal) {
        folderService.deleteFolder(id, principal.getName()); // проверка владельца
        return ResponseEntity.ok("Удалено");
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<FolderDto> renameFolder(
            @PathVariable Long id,
            @RequestBody FolderDto dto,
            Principal principal) {

        var updatedFolder = folderService.renameFolder(id, dto.getName(), principal.getName()); // проверка владельца
        return ResponseEntity.ok(updatedFolder);
    }
}

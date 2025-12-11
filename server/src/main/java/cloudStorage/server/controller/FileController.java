package cloudStorage.server.controller;

import cloudStorage.server.db.entity.File;
import cloudStorage.server.model.FileDto;
import cloudStorage.server.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<FileDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            Principal principal) {

        var savedFile = fileService.uploadFile(file, folderId, principal.getName());
        return ResponseEntity.ok(fileService.toDto(savedFile));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id, Principal principal) {

        var entity = fileService.getFileEntity(id, principal.getName());
        byte[] bytes = fileService.download(id, principal.getName());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + entity.getFileName() + "\"")
                .body(bytes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFile(@PathVariable Long id, Principal principal) {
        fileService.deleteFile(id, principal.getName());
        return ResponseEntity.ok("Удалено");
    }

    @GetMapping({"/folder", "/folder/{folderId}"})
    public ResponseEntity<List<FileDto>> listFiles(
            @PathVariable(required = false) Long folderId,
            Principal principal) {

        var files = fileService.listFiles(folderId, principal.getName());
        List<FileDto> dtos = files.stream()
                .map(fileService::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<FileDto> renameFile(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Principal principal) {

        String newName = body.get("name");
        var updatedFile = fileService.renameFile(id, newName, principal.getName());
        return ResponseEntity.ok(fileService.toDto(updatedFile));
    }
}

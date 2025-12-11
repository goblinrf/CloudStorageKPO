package cloudStorage.server.service;

import cloudStorage.server.db.entity.Folder;
import cloudStorage.server.db.entity.User;
import cloudStorage.server.db.jpaRepository.FolderRepository;
import cloudStorage.server.db.jpaRepository.UserRepository;
import cloudStorage.server.model.FolderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    // -----------------------------
    // Конвертация Folder → FolderDto
    // -----------------------------
    public FolderDto toDto(Folder folder) {
        FolderDto dto = new FolderDto();
        dto.setId(folder.getId());
        dto.setName(folder.getName());
        dto.setParentId(folder.getParent() != null ? folder.getParent().getId() : null);
        return dto;
    }

    // -----------------------------
    // Создать папку
    // -----------------------------
    public FolderDto createFolder(FolderDto dto, String username) {

        User user = userRepository.findByName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Folder folder = new Folder();
        folder.setName(dto.getName());
        folder.setOwner(user);

        if (dto.getParentId() != null) {
            Folder parent = folderRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent folder not found"));
            folder.setParent(parent);
        }

        Folder saved = folderRepository.save(folder);
        return toDto(saved);
    }

    // -----------------------------
    // Получить все корневые папки пользователя
    // -----------------------------
    public List<FolderDto> listUserRootFolders(String username) {
        User user = userRepository.findByName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return folderRepository.findAllByOwner(user).stream()
                .filter(f -> f.getParent() == null)
                .map(this::toDto)
                .toList();
    }

    // -----------------------------
    // Получить вложенные папки
    // -----------------------------
    public List<FolderDto> listSubFolders(Long parentId) {

        Folder parent = folderRepository.findById(parentId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        return folderRepository.findAllByParent(parent).stream()
                .map(this::toDto)
                .toList();
    }

    // -----------------------------
    // Удалить папку
    // -----------------------------
    public void deleteFolder(Long id, String username) {

        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!folder.getOwner().getName().equals(username))
            throw new RuntimeException("Access denied");

        folderRepository.delete(folder);
    }
    public FolderDto renameFolder(Long id, String newName, String username) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!folder.getOwner().getName().equals(username))
            throw new RuntimeException("Access denied");

        folder.setName(newName);
        folderRepository.save(folder);

        return toDto(folder);
    }
}

package cloudStorage.server.service;

import cloudStorage.server.db.entity.Folder;
import cloudStorage.server.db.entity.User;
import cloudStorage.server.db.jpaRepository.FileRepository;
import cloudStorage.server.db.jpaRepository.FolderRepository;
import cloudStorage.server.db.jpaRepository.UserRepository;
import cloudStorage.server.model.FolderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;

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
    // Создать папку (проверка владельца родителя)
    // -----------------------------
    public FolderDto createFolder(FolderDto dto, String username) {
        User user = userRepository.findByName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));

        Folder folder = new Folder();
        folder.setName(dto.getName());
        folder.setOwner(user);

        if (dto.getParentId() != null) {
            Folder parent = folderRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Родительская папка не найдена"));

            // родитель должен принадлежать тому же пользователю
            if (parent.getOwner() == null || !parent.getOwner().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет прав на создание внутри указанной папки");
            }
            folder.setParent(parent);
        }

        Folder saved = folderRepository.save(folder);
        return toDto(saved);
    }

    // -----------------------------
    // Список корневых папок пользователя
    // -----------------------------
    public List<FolderDto> listUserRootFolders(String username) {
        User user = userRepository.findByName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));

        return folderRepository.findAllByOwner(user).stream()
                .filter(f -> f.getParent() == null)
                .map(this::toDto)
                .toList();
    }

    // -----------------------------
    // Проверка владения
    // -----------------------------
    public boolean isOwner(Long folderId, String username) {
        if (folderId == null) return false;
        return folderRepository.findById(folderId)
                .map(f -> f.getOwner() != null && f.getOwner().getName().equals(username))
                .orElse(false);
    }

    // -----------------------------
    // Получить вложенные папки (проверка владельца родителя)
    // -----------------------------
    public List<FolderDto> listSubFolders(Long parentId) {
        Folder parent = folderRepository.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена"));

        // Примечание: проверку владельца вызывает контроллер / сервис уровня контроллера.
        // Если требуется — можно добавить username параметр и проверять здесь.

        return folderRepository.findAllByParent(parent).stream()
                .map(this::toDto)
                .toList();
    }

    // -----------------------------
    // Удаление папки (только владелец), защита от удаления с содержимым
    // -----------------------------
    public void deleteFolder(Long id, String username) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена"));

        if (folder.getOwner() == null || !folder.getOwner().getName().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ запрещён");
        }

        // нельзя удалять, если есть вложенные папки
        boolean hasChildFolders = !folderRepository.findAllByParent(folder).isEmpty();
        if (hasChildFolders) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Папка содержит вложенные папки. Удаление запрещено.");
        }

        // нельзя удалять, если в папке есть файлы
        boolean hasFiles = !fileRepository.findAllByFolder(folder).isEmpty();
        if (hasFiles) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Папка содержит файлы. Удаление запрещено.");
        }

        folderRepository.delete(folder);
    }

    // -----------------------------
    // Переименование (только владелец)
    // -----------------------------
    public FolderDto renameFolder(Long id, String newName, String username) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена"));

        if (folder.getOwner() == null || !folder.getOwner().getName().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ запрещён");
        }

        folder.setName(newName);
        folderRepository.save(folder);
        return toDto(folder);
    }
}

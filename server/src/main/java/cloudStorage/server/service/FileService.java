package cloudStorage.server.service;

import cloudStorage.server.db.entity.File;
import cloudStorage.server.db.entity.Folder;
import cloudStorage.server.db.entity.User;
import cloudStorage.server.db.jpaRepository.FileRepository;
import cloudStorage.server.db.jpaRepository.FolderRepository;
import cloudStorage.server.db.jpaRepository.UserRepository;
import cloudStorage.server.model.FileDto;
import cloudStorage.server.model.UsersDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;

    public File uploadFile(MultipartFile multipartFile, Long folderId, String username) {
        User user = userRepository.findByName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
        }

        // формируем путь на диске
        String storagePath = "E:/CloudStorage";
        String folderPath = storagePath + "/users/" + user.getId() + "/folders/" +
                (folderId == null ? "root" : folderId);

        Path dirPath = Paths.get(folderPath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create folder on disk", e);
        }

        String fileNameOnDisk = UUID.randomUUID() + "_" + multipartFile.getOriginalFilename();
        Path filePath = dirPath.resolve(fileNameOnDisk);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(multipartFile.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("File upload failed", e);
        }

        File entity = new File();
        entity.setFileName(multipartFile.getOriginalFilename());
        entity.setS3Key(filePath.toString()); // теперь path на диске
        entity.setFolder(folder);
        entity.setUser(user);
        entity.setSize(multipartFile.getSize());
        entity.setContentType(multipartFile.getContentType());

        return fileRepository.save(entity);
    }

    public File getFileEntity(Long fileId, String username) {
        File entity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!entity.getUser().getName().equals(username)) {
            throw new RuntimeException("Access denied");
        }

        return entity;
    }

    public byte[] download(Long fileId, String username) {
        File entity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!entity.getUser().getName().equals(username))
            throw new RuntimeException("Access denied");

        try {
            return Files.readAllBytes(Paths.get(entity.getS3Key()));
        } catch (IOException e) {
            throw new RuntimeException("File read failed", e);
        }
    }

    public void deleteFile(Long fileId, String username) {
        File entity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!entity.getUser().getName().equals(username))
            throw new RuntimeException("Access denied");

        try {
            Files.deleteIfExists(Paths.get(entity.getS3Key()));
        } catch (IOException e) {
            throw new RuntimeException("File delete failed", e);
        }

        fileRepository.delete(entity);
    }

    public List<File> listFiles(Long folderId, String username) {
        if (folderId == null) {
            User user = userRepository.findByName(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return fileRepository.findAllByUserAndFolderIsNull(user);
        }

        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!folder.getOwner().getName().equals(username))
            throw new RuntimeException("Access denied");

        return fileRepository.findAllByFolder(folder);
    }

    public File renameFile(Long id, String newName, String username) {
        File file = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!file.getUser().getName().equals(username))
            throw new RuntimeException("Access denied");

        file.setFileName(newName);
        return fileRepository.save(file);
    }
    public FileDto toDto(File file) {
        FileDto dto = new FileDto();
        dto.setId(file.getId());
        dto.setFileName(file.getFileName());
        dto.setS3Key(file.getS3Key());
        dto.setSize(file.getSize());
        dto.setContentType(file.getContentType());

        UsersDto userDto = new UsersDto();
        userDto.setId(file.getUser().getId());
        userDto.setUsername(file.getUser().getName());
        dto.setUser(userDto);

        return dto;
    }
}

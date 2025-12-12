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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;

    @Value("${app.storage.path:E:/CloudStorage}")
    private String storagePathRoot;

    @Value("${app.upload.max-size-bytes:20971520}") // 20MB default
    private long maxUploadSize;

    @Value("${app.upload.allowed-extensions:txt,pdf,jpg,jpeg,png,doc,docx,xlsx,pptx,odt}")
    private String allowedExtensionsProp;

    private Set<String> allowedExtensions() {
        return Arrays.stream(allowedExtensionsProp.split(","))
                .map(String::toLowerCase)
                .map(s -> s.startsWith(".") ? s.substring(1) : s)
                .collect(Collectors.toSet());
    }

    // -----------------------------
    // Загрузка файла (безопасно)
    // -----------------------------
    public File uploadFile(MultipartFile multipartFile, Long folderId, String username) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл не выбран");
        }

        if (multipartFile.getSize() > maxUploadSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Файл превышает максимально допустимый размер");
        }

        String originalName = StringUtils.cleanPath(Objects.requireNonNull(multipartFile.getOriginalFilename()));
        if (originalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимое имя файла");
        }

        String ext = getExtension(originalName).orElse("");
        if (!ext.isEmpty() && !allowedExtensions().contains(ext.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Тип файла не разрешён");
        }

        User user = userRepository.findByName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));

        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена"));

            // Проверка принадлежности папки пользователю
            if (!Objects.equals(folder.getOwner().getId(), user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к указанной папке");
            }
        }

        // Формируем относительный путь для хранения (без раскрытия абсолютного)
        String sanitized = sanitizeFileName(originalName);
        String uuid = UUID.randomUUID().toString();
        String relativeDir = String.format("users/%d/folders/%s", user.getId(), folderId == null ? "root" : folderId.toString());
        String storedFileName = uuid + "_" + sanitized;
        Path dirPath = Paths.get(storagePathRoot).resolve(relativeDir);

        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось создать директорию для хранения файла");
        }

        Path filePath = dirPath.resolve(storedFileName);

        // Защита от path traversal: filePath.normalize() должен начинаться с dirPath.normalize()
        Path normalizedDir = dirPath.normalize().toAbsolutePath();
        Path normalizedFile = filePath.normalize().toAbsolutePath();
        if (!normalizedFile.startsWith(normalizedDir)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Нарушение безопасности пути");
        }

        try (FileOutputStream fos = new FileOutputStream(normalizedFile.toFile())) {
            fos.write(multipartFile.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка сохранения файла");
        }

        File entity = new File();
        entity.setFileName(sanitized);
        // сохраняем **относительный** путь — чтобы не раскрывать диск/путь в DTO
        entity.setS3Key(relativeDir + "/" + storedFileName);
        entity.setFolder(folder);
        entity.setUser(user);
        entity.setSize(multipartFile.getSize());
        entity.setContentType(multipartFile.getContentType());

        return fileRepository.save(entity);
    }

    // -----------------------------
    // Вспомогательные
    // -----------------------------
    private Optional<String> getExtension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx > 0 && idx < name.length() - 1) {
            return Optional.of(name.substring(idx + 1));
        }
        return Optional.empty();
    }

    private String sanitizeFileName(String name) {
        // взять только file name (без путей) и заменить запрещённые символы
        String base = Paths.get(name).getFileName().toString();
        // разрешим буквы, цифры, пробел, точку, дефис, подчёркивание
        return base.replaceAll("[^A-Za-z0-9А-Яа-яёЁ \\._\\-]", "_");
    }

    private Path resolveAbsolutePath(String relative) {
        // защита от path traversal
        Path p = Paths.get(storagePathRoot).resolve(relative).normalize().toAbsolutePath();
        Path root = Paths.get(storagePathRoot).toAbsolutePath().normalize();
        if (!p.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Нарушение безопасности пути");
        }
        return p;
    }

    // -----------------------------
    // Проверка владельца папки (универсально)
    // -----------------------------
    public boolean isFolderOwner(Long folderId, String username) {
        if (folderId == null) return false;
        return folderRepository.findById(folderId)
                .map(f -> f.getOwner() != null && f.getOwner().getName().equals(username))
                .orElse(false);
    }

    // -----------------------------
    // Получение сущности файла (только владелец)
    // -----------------------------
    public File getFileEntity(Long fileId, String username) {
        File entity = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден"));

        if (entity.getUser() == null || !entity.getUser().getName().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ запрещён");
        }

        return entity;
    }

    // -----------------------------
    // Скачивание
    // -----------------------------
    public byte[] download(Long fileId, String username) {
        File entity = getFileEntity(fileId, username);

        Path abs = resolveAbsolutePath(entity.getS3Key());
        if (!Files.exists(abs)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл на сервере не найден");
        }

        try {
            return Files.readAllBytes(abs);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка чтения файла");
        }
    }

    // -----------------------------
    // Удаление
    // -----------------------------
    public void deleteFile(Long fileId, String username) {
        File entity = getFileEntity(fileId, username);

        Path abs = resolveAbsolutePath(entity.getS3Key());
        try {
            Files.deleteIfExists(abs);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка удаления файла на диске");
        }

        fileRepository.delete(entity);
    }

    // -----------------------------
    // Список файлов по папке (безопасно)
    // -----------------------------
    public List<File> listFiles(Long folderId, String username) {
        if (folderId == null) {
            User user = userRepository.findByName(username)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));
            return fileRepository.findAllByUserAndFolderIsNull(user);
        }

        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена"));

        if (folder.getOwner() == null || !folder.getOwner().getName().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ запрещён");
        }

        return fileRepository.findAllByFolder(folder);
    }

    // -----------------------------
    // Переименование файла (только владелец)
    // -----------------------------
    public File renameFile(Long id, String newName, String username) {
        if (newName == null || newName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новое имя пусто");
        }

        File file = fileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден"));

        if (file.getUser() == null || !file.getUser().getName().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ запрещён");
        }

        String sanitized = sanitizeFileName(newName);
        file.setFileName(sanitized);
        return fileRepository.save(file);
    }

    // -----------------------------
    // DTO
    // -----------------------------
    public FileDto toDto(File file) {
        FileDto dto = new FileDto();
        dto.setId(file.getId());
        dto.setFileName(file.getFileName());
        // не отдаём абсолютный путь — только относительный s3Key (если нужно скрыть вообще, можно убрать)
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

package cloudStorage.server.controller;

import cloudStorage.server.db.entity.File;
import cloudStorage.server.db.entity.Folder;
import cloudStorage.server.db.entity.User;
import cloudStorage.server.db.jpaRepository.FileRepository;
import cloudStorage.server.db.jpaRepository.FolderRepository;
import cloudStorage.server.db.jpaRepository.UserRepository;
import cloudStorage.server.model.FolderDto;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FolderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private FileRepository fileRepository;

    private User testUser;

    @BeforeAll
    void setupUser() {
        testUser = userRepository.findByName("testuser")
                .orElseGet(() -> {
                    User u = new User();
                    u.setName("testuser");
                    u.setPassword("password");
                    return userRepository.save(u);
                });
    }

    @AfterEach
    void cleanupFolders() {
        // рекурсивно удалить все папки пользователя
        List<Folder> allFolders = folderRepository.findAll();
        allFolders.forEach(folder -> deleteFolderRecursively(folder));
    }
    private void deleteFolderRecursively(Folder folder) {
        // сначала удаляем файлы
        fileRepository.findAllByFolder(folder).forEach(file -> {
            try { Files.deleteIfExists(Paths.get(file.getS3Key())); } catch (Exception ignored) {}
            fileRepository.delete(file);
        });

        // потом рекурсивно удаляем дочерние папки
        folderRepository.findAllByParent(folder).forEach(this::deleteFolderRecursively);

        folderRepository.delete(folder);
    }

    @Test
    @WithMockUser(username = "testuser")
    void createFolderTest() throws Exception {
        mockMvc.perform(post("/api/folders/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MyFolder\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MyFolder"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void listRootFoldersTest() throws Exception {
        Folder folder = new Folder();
        folder.setName("RootFolder");
        folder.setOwner(testUser);
        folderRepository.save(folder);

        mockMvc.perform(get("/api/folders/root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("RootFolder"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void listSubfoldersTest() throws Exception {
        Folder parent = new Folder();
        parent.setName("Parent");
        parent.setOwner(testUser);
        parent = folderRepository.save(parent);

        Folder child = new Folder();
        child.setName("Child");
        child.setOwner(testUser);
        child.setParent(parent);
        folderRepository.save(child);

        mockMvc.perform(get("/api/folders/" + parent.getId() + "/sub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Child"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void renameFolderTest() throws Exception {
        Folder folder = new Folder();
        folder.setName("OldName");
        folder.setOwner(testUser);
        folder = folderRepository.save(folder);

        mockMvc.perform(put("/api/folders/" + folder.getId() + "/rename")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NewName\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NewName"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteFolderTest() throws Exception {
        Folder folder = new Folder();
        folder.setName("ToDelete");
        folder.setOwner(testUser);
        folder = folderRepository.save(folder);

        mockMvc.perform(delete("/api/folders/" + folder.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string("Удалено"));

        Optional<Folder> deleted = folderRepository.findById(folder.getId());
        Assertions.assertTrue(deleted.isEmpty());
    }
}

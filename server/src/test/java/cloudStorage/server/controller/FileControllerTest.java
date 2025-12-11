package cloudStorage.server.controller;

import cloudStorage.server.db.entity.File;
import cloudStorage.server.db.entity.User;
import cloudStorage.server.db.jpaRepository.FileRepository;
import cloudStorage.server.db.jpaRepository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    private User testUser;

    @BeforeAll
    void setupUser() {
        testUser = userRepository.findByName("testuser")
                .orElseGet(() -> {
                    User u = new User();
                    u.setName("testuser");
                    u.setPassword("password"); // для теста достаточно простой пароль
                    return userRepository.save(u);
                });
    }

    @AfterEach
    void cleanupFiles() {
        // удаляем все файлы, созданные тестами
        fileRepository.findByUserId(testUser.getId()).forEach(file -> {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(file.getS3Key()));
            } catch (Exception ignored) {}
            fileRepository.delete(file);
        });
    }

    @Test
    @WithMockUser(username = "testuser")
    void uploadAndListFile() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello World".getBytes(StandardCharsets.UTF_8)
        );

        // загрузка файла
        mockMvc.perform(multipart("/api/files/upload")
                        .file(multipartFile))
                .andExpect(status().isOk())
                .andExpect((ResultMatcher) jsonPath("$.fileName").value("test.txt"));

        // проверка, что файл появился в списке
        File file = fileRepository.findByUserId(testUser.getId()).get(0);

        mockMvc.perform(get("/api/files/folder" + (file.getFolder() == null ? "" : file.getFolder().getId())))
                .andExpect(status().isOk())
                .andExpect((ResultMatcher) jsonPath("$", hasSize(1)))
                .andExpect((ResultMatcher) jsonPath("$[0].fileName").value("test.txt"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void downloadFileTest() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "download.txt", MediaType.TEXT_PLAIN_VALUE, "Download me".getBytes(StandardCharsets.UTF_8)
        );

        File file = new File();
        file.setFileName("download.txt");
        file.setUser(testUser);
        file.setS3Key("E:/CloudStorage/test-download.txt"); // временный путь
        file.setSize(14);
        file.setContentType(MediaType.TEXT_PLAIN_VALUE);

        file = fileRepository.save(file);

        java.nio.file.Files.write(java.nio.file.Paths.get(file.getS3Key()), "Download me".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/files/" + file.getId() + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"download.txt\""));
    }

    @Test
    @WithMockUser(username = "testuser")
    void renameAndDeleteFileTest() throws Exception {
        File file = new File();
        file.setFileName("oldname.txt");
        file.setUser(testUser);
        file.setS3Key("E:/CloudStorage/oldname.txt");
        file.setSize(0);
        file.setContentType(MediaType.TEXT_PLAIN_VALUE);
        file = fileRepository.save(file);

        java.nio.file.Files.createFile(java.nio.file.Paths.get(file.getS3Key()));

        // переименование
        mockMvc.perform(put("/api/files/" + file.getId() + "/rename")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"newname.txt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("newname.txt"));

        // удаление
        mockMvc.perform(delete("/api/files/" + file.getId()))
                .andExpect(status().isOk())
                .andExpect((ResultMatcher) content().string("Удалено"));

        Optional<File> deleted = fileRepository.findById(file.getId());
        Assertions.assertTrue(deleted.isEmpty());
    }
}

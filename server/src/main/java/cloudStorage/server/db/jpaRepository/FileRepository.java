package cloudStorage.server.db.jpaRepository;


import cloudStorage.server.db.entity.File;
import cloudStorage.server.db.entity.Folder;
import cloudStorage.server.db.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileRepository extends JpaRepository<File, Long> {
    List<File> findByUserId(Long userId);
    List<File> findAllByFolder(Folder folder);
    List<File> findAllByUserAndFolderIsNull(User user);
}

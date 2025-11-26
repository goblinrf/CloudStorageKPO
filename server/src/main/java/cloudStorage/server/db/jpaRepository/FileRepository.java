package cloudStorage.server.db.jpaRepository;


import cloudStorage.server.db.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileRepository extends JpaRepository<File, Long> {
    List<File> findByUserId(Long userId);
}

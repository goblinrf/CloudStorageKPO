package cloudStorage.server.db.jpaRepository;

import cloudStorage.server.db.entity.Folder;
import cloudStorage.server.db.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findAllByOwner(User owner);
    List<Folder> findAllByParent(Folder parent);

}

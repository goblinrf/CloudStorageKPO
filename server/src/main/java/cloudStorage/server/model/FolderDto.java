package cloudStorage.server.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FolderDto {
    private Long id;
    private String name;
    private Long parentId;
}

package cloudStorage.server.model;

import cloudStorage.server.db.entity.File;
import lombok.Data;

@Data
public class FileDto {
    private Long id;
    private String fileName;
    private String s3Key;
    private Long size;
    private String contentType;
    private UsersDto user;
}

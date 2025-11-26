package cloudStorage.server.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsersDto {

    private String username;
    private String rawPassword;

    public UsersDto(String username, String rawPassword) {
        this.username = username;
        this.rawPassword = rawPassword;
    }


    public UsersDto() {
    }
}


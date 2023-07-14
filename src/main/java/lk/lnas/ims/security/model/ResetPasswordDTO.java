package lk.lnas.ims.security.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordDTO {
    private String token;
    private String username;
    private String email;
    private String password;
}

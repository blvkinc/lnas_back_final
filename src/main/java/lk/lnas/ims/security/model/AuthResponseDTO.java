package lk.lnas.ims.security.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class AuthResponseDTO {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Boolean isMfaRequired;
    private Date expiresIn;
    private String username;
    private String firstName;
    private String lastName;
    private String role;
}

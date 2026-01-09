package ro.mateistanescu.matquizspringbootbackend.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRegisterDto {
    @NotBlank
    private String username;

    @NotBlank
    private String email;

    @NotBlank
    private String password;
}

package ro.mateistanescu.matquizspringbootbackend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeEmailRequestDto {
    @NotBlank
    @Email
    @Size(max = 100)
    private String newEmail;
}

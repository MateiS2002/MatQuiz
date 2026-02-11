package ro.mateistanescu.matquizspringbootbackend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeUsernameRequestDto {
    @NotBlank
    @Size(min = 3, max = 30)
    private String newUsername;
}

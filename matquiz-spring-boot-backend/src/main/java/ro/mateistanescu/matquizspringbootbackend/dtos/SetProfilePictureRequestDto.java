package ro.mateistanescu.matquizspringbootbackend.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetProfilePictureRequestDto {
    @NotBlank
    private String avatarUrl;
}

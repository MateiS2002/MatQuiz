package ro.mateistanescu.matquizspringbootbackend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailFormDto {
    @Email
    @NotBlank
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 3, max = 60)
    private String nickname;

    @NotBlank
    @Size(min = 3, max = 100)
    private String topic;

    @NotBlank
    @Size(min = 10, max = 1000)
    private String message;

    @NotBlank
    @Size(min = 1, max = 10)
    private String appVersion;
}

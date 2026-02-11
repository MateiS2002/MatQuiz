package ro.mateistanescu.matquizspringbootbackend.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.user.avatar")
public class UserAvatarProperties {
    @NotBlank
    private String baseUrl;

    @Min(1)
    private int imageCount;
}

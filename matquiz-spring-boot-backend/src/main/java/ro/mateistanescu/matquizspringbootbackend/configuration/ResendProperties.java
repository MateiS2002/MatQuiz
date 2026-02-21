package ro.mateistanescu.matquizspringbootbackend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.resend")
public class ResendProperties {
    private String apiKey = "";
    private String from = "MatQuiz <contact@matquiz.mateistanescu.ro>";
    private String to = "delivered@resend.dev";
}

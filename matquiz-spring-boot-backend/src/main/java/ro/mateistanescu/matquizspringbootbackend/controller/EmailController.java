package ro.mateistanescu.matquizspringbootbackend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.mateistanescu.matquizspringbootbackend.dtos.EmailFormDto;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.exception.RateLimitExceededException;
import ro.mateistanescu.matquizspringbootbackend.service.EmailService;
import ro.mateistanescu.matquizspringbootbackend.service.UserService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
@Slf4j
public class EmailController {
    private final EmailService emailService;
    private final UserService userService;

    @PostMapping("/send")
    public ResponseEntity<?> sendEmail(Principal principal, @RequestBody @Valid EmailFormDto emailFormDto){
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(principal.getName());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String emailId = emailService.sendContactEmail(emailFormDto, user);
            return ResponseEntity.ok(Map.of("message", "Email sent successfully.", "emailId", emailId));
        } catch (RateLimitExceededException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("EmailRateLimit-Limit", "1")
                    .header("EmailRateLimit-Remaining", "0")
                    .header("EmailRateLimit-Reset", String.valueOf(exception.getRetryAfterSeconds()))
                    .header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
                    .body(Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", exception.getMessage()));
        }
    }
}

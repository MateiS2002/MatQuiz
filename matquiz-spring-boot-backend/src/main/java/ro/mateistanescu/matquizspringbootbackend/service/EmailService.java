package ro.mateistanescu.matquizspringbootbackend.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import ro.mateistanescu.matquizspringbootbackend.configuration.ResendProperties;
import ro.mateistanescu.matquizspringbootbackend.dtos.EmailFormDto;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.exception.RateLimitExceededException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralizes contact-email delivery through Resend to keep controllers thin and
 * enforce one place for provider configuration, payload shaping, and error mapping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private static final long CONTACT_EMAIL_LIMIT_PER_MINUTE = 1L;

    private final Resend resend;
    private final ResendProperties resendProperties;
    private final Map<Long, Bucket> contactBucketsByUserId = new ConcurrentHashMap<>();

    public String sendContactEmail(EmailFormDto request, User user) {
        if (!StringUtils.hasText(resendProperties.getApiKey())) {
            throw new IllegalStateException("Resend API key is not configured. Set RESEND_API_KEY.");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String timestamp = LocalDateTime.now(ZoneId.of("Europe/Bucharest")).format(formatter);

        validateAuthenticatedIdentity(request, user);
        enforceContactRateLimit(user);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(resendProperties.getFrom())
                .to(resendProperties.getTo())
                .replyTo(user.getEmail().trim())
                .subject("[MatQuiz Contact] " + request.getTopic().trim() + " -- " + timestamp)
                .html(buildHtmlBody(request, user))
                .build();

        try {
            CreateEmailResponse response = resend.emails().send(params);
            return response.getId();
        } catch (ResendException exception) {
            log.error("Failed to send contact email via Resend", exception);
            throw new IllegalStateException("Email delivery failed. Please try again later.");
        }
    }

    private void validateAuthenticatedIdentity(EmailFormDto request, User user) {
        String normalizedRequestEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String normalizedUserEmail = user.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!normalizedRequestEmail.equals(normalizedUserEmail)) {
            throw new IllegalArgumentException("Request email does not match the authenticated account.");
        }

        String normalizedRequestNickname = request.getNickname().trim().toLowerCase(Locale.ROOT);
        String normalizedUsername = user.getUsername().trim().toLowerCase(Locale.ROOT);
        if (!normalizedRequestNickname.equals(normalizedUsername)) {
            throw new IllegalArgumentException("Request nickname does not match the authenticated account.");
        }
    }

    private void enforceContactRateLimit(User user) {
        Bucket bucket = contactBucketsByUserId.computeIfAbsent(user.getId(), key -> createContactBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return;
        }

        long secondsToWait = Math.max(1L, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
        throw new RateLimitExceededException("You can send only one contact message per minute.", secondsToWait);
    }

    private Bucket createContactBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(CONTACT_EMAIL_LIMIT_PER_MINUTE)
                .refillGreedy(CONTACT_EMAIL_LIMIT_PER_MINUTE, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String buildHtmlBody(EmailFormDto request, User user) {
        String nickname = HtmlUtils.htmlEscape(user.getUsername().trim());
        String email = HtmlUtils.htmlEscape(user.getEmail().trim());
        String topic = HtmlUtils.htmlEscape(request.getTopic().trim());
        String appVersion = HtmlUtils.htmlEscape(request.getAppVersion().trim());
        String message = HtmlUtils.htmlEscape(request.getMessage().trim()).replace("\n", "<br/>");

        return "<h2>MatQuiz Contact Form</h2>"
                + "<p><strong>Nickname:</strong> " + nickname + "</p>"
                + "<p><strong>Email:</strong> " + email + "</p>"
                + "<p><strong>Topic:</strong> " + topic + "</p>"
                + "<p><strong>App Version:</strong> " + appVersion + "</p>"
                + "<p><strong>Message:</strong><br/>" + message + "</p>";
    }
}

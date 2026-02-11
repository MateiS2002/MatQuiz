package ro.mateistanescu.matquizspringbootbackend.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.service.UserService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Intercepts incoming STOMP messages to handle Authentication.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final UserService userService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Optional<String> token = extractBearerToken(accessor.getNativeHeader("Authorization"));

            if (token.isPresent()) {
                try {
                    User user = userService.validateUser(token.get());

                    if (user != null) {
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().name());

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                Collections.singletonList(authority)
                        );

                        accessor.setUser(authentication);
                        log.info("WebSocket Authenticated: {}", user.getUsername());
                    } else {
                        log.warn("WebSocket Authentication Failed: User not found");
                        return null;
                    }
                } catch (Exception e) {
                    log.warn("WebSocket Authentication Failed: {}", e.getMessage());
                    return null;
                }
            } else {
                log.warn("WebSocket Authentication Failed: Missing or malformed Authorization header");
                return null;
            }
        }
        return message;
    }

    private Optional<String> extractBearerToken(List<String> authorizationHeaders) {
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            return Optional.empty();
        }

        String header = authorizationHeaders.get(0);
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(token);
    }
}

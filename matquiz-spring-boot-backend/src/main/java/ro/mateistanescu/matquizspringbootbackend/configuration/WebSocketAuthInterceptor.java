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

        assert accessor != null;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authorization = accessor.getNativeHeader("Authorization");

            if (authorization != null && !authorization.isEmpty()) {
                String token = authorization.get(0).substring(7); // Remove "Bearer "

                try {
                    User user = userService.validateUser(token);

                    if (user != null) {
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().name());

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                Collections.singletonList(authority)
                        );

                        accessor.setUser(authentication);
                        log.info("WebSocket Authenticated: {}", user.getUsername());
                    }
                } catch (Exception e) {
                    log.error("WebSocket Authentication Failed: {}", e.getMessage());
                }
            }
        }
        return message;
    }
}
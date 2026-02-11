package ro.mateistanescu.matquizspringbootbackend.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.Role;
import ro.mateistanescu.matquizspringbootbackend.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private UserService userService;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(userService);
    }

    @Test
    @DisplayName("CONNECT with valid bearer token authenticates WebSocket user")
    void connectWithValidBearerTokenAuthenticatesUser() {
        User user = User.builder()
                .id(10L)
                .username("john")
                .email("john@example.com")
                .passwordHash("encoded")
                .role(Role.ROLE_USER)
                .build();

        when(userService.validateUser("valid-token")).thenReturn(user);

        Message<?> message = connectMessage("Bearer valid-token");
        Message<?> interceptedMessage = interceptor.preSend(message, messageChannel);

        assertNotNull(interceptedMessage);
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(interceptedMessage, StompHeaderAccessor.class);
        assertNotNull(accessor);
        assertNotNull(accessor.getUser());

        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) accessor.getUser();

        assertEquals("john", authentication.getName());
        assertSame(user, authentication.getPrincipal());
    }

    @Test
    @DisplayName("CONNECT without authorization header is rejected")
    void connectWithoutAuthorizationHeaderIsRejected() {
        Message<?> interceptedMessage = interceptor.preSend(connectMessage(null), messageChannel);
        assertNull(interceptedMessage);
    }

    @Test
    @DisplayName("CONNECT with malformed authorization header is rejected")
    void connectWithMalformedAuthorizationHeaderIsRejected() {
        Message<?> interceptedMessage = interceptor.preSend(connectMessage("Token invalid-format"), messageChannel);
        assertNull(interceptedMessage);
    }

    @Test
    @DisplayName("CONNECT with invalid token is rejected")
    void connectWithInvalidTokenIsRejected() {
        when(userService.validateUser("invalid-token"))
                .thenThrow(new UsernameNotFoundException("Invalid token"));

        Message<?> interceptedMessage = interceptor.preSend(connectMessage("Bearer invalid-token"), messageChannel);
        assertNull(interceptedMessage);
    }

    @Test
    @DisplayName("Non-CONNECT messages are ignored by authentication interceptor")
    void nonConnectMessagesAreIgnored() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> interceptedMessage = interceptor.preSend(message, messageChannel);
        assertNotNull(interceptedMessage);
        assertSame(message, interceptedMessage);
    }

    private Message<byte[]> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}

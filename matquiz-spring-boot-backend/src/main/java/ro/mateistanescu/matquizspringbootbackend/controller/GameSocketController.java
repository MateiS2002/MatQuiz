package ro.mateistanescu.matquizspringbootbackend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.JoinRoomRequest;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;
import ro.mateistanescu.matquizspringbootbackend.service.GameService;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GameSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameService gameService;
    private final GameMapper gameMapper;

    /**
     * 1. CREATE LOBBY
     * Client sends: /app/create
     * Server returns: a room object
     */
    @MessageMapping("/create")
    public void createRoom(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        User host = getUser(principal);
        String sessionID = headerAccessor.getSessionId();

        try {
            GameRoom room = gameService.createRoom(host, sessionID);

            GameRoomDto roomDto = gameMapper.toDto(room);

            //sends the full room object to the host
            messagingTemplate.convertAndSendToUser(
                    host.getUsername(),
                    "/queue/created",
                    roomDto
            );
        } catch (Exception e) {
            sendError(host.getUsername(), "Failed to create room: " + e.getMessage());
        }
    }

    /**
     * 2. JOIN LOBBY
     * Client sends: /app/join { "roomCode": "ABC123" }
     * Server returns: The full Room object for the client to render and a general message to all other players
     */
    @MessageMapping("/join")
    public void joinRoom(@Payload JoinRoomRequest request, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        User user = getUser(principal);
        String sessionID = headerAccessor.getSessionId();

        try {
            String roomCode = request.getRoomCode().trim().toUpperCase();
            GameRoom room = gameService.joinRoom(user, roomCode, sessionID);

            GameRoomDto roomDto = gameMapper.toDto(room);

            messagingTemplate.convertAndSendToUser(
                    user.getUsername(),
                    "/queue/joined",
                    roomDto
            );

            messagingTemplate.convertAndSend(
                    "/topic/room/" + room.getRoomCode(),
                    roomDto
            );

        } catch (Exception e) {
            sendError(user.getUsername(), e.getMessage());
        }
    }

    /**
     * 3. RECONNECT (Manual Trigger)
     * Client sends: /app/reconnect
     * Server returns: The Room DTO (if active game exists)
     */
    @MessageMapping("/reconnect")
    public void reconnect(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        if (principal == null) return;
        User user = getUser(principal);
        String sessionID = headerAccessor.getSessionId();

        try {
            GameRoom room = gameService.handleReconnect(user.getUsername(), sessionID);

            if (room != null) {
                GameRoomDto roomDto = gameMapper.toDto(room);

                messagingTemplate.convertAndSendToUser(
                        user.getUsername(),
                        "/queue/reconnected",
                        roomDto
                );

                // Notify others that the player is back
                messagingTemplate.convertAndSend(
                        "/topic/room/" + room.getRoomCode(),
                        roomDto
                );
            }
        } catch (Exception e) {
            log.error("Reconnect failed for user {}", user.getUsername(), e);
            sendError(user.getUsername(), "Reconnect failed: " + e.getMessage());
        }
    }

    private User getUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            return (User) auth.getPrincipal();
        }
        throw new IllegalStateException("User not authenticated");
    }

    private void sendError(String username, String message) {
        log.error("Error for user {}: {}", username, message);
        messagingTemplate.convertAndSendToUser(username, "/queue/errors", message);
    }
}

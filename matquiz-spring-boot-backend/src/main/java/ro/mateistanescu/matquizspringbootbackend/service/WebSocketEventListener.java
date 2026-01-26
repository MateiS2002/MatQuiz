package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameMapper gameMapper;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("SessionDisconnectEvent received for SID: {}", sessionId);

        GameRoom room = gameService.handleDisconnect(sessionId);

        if (room != null) {
            log.info("Broadcasting disconnect update for room: {}", room.getRoomCode());

            GameRoomDto roomDto = gameMapper.toDto(room);

            messagingTemplate.convertAndSend(
                    "/topic/room/" + room.getRoomCode(),
                    roomDto
            );
        }
    }
}
package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.QuizResultMessage;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizResultListener {

    private final GameService gameService;
    private final GameMapper gameMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public void receiveGeneratedQuiz(QuizResultMessage message){
        log.info("RABBITMQ: Received generated quiz for room: {}", message.getRoomCode());

        try {
            GameRoom updatedRoom = gameService.processQuizResult(message);

            GameRoomDto roomDto = gameMapper.toDto(updatedRoom);

            messagingTemplate.convertAndSend(
                    "/topic/room/" + updatedRoom.getRoomCode(),
                    roomDto
            );

            log.info("BROADCAST: Sent updated GameRoomDto to room {}", updatedRoom.getRoomCode());

        } catch (Exception e) {
            log.error("ERROR: Failed to process quiz results for room {}", message.getRoomCode(), e);
        }
    }
}

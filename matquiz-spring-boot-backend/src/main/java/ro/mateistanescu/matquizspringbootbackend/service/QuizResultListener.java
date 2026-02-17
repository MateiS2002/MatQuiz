package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ro.mateistanescu.matquizspringbootbackend.configuration.RabbitMqConfig;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.QuizResultMessage;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizResultListener {
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String GENERATION_UNAVAILABLE_MESSAGE =
            "MatQuiz AI generation service is currently unavailable. Please check back later. We are sorry.";

    private final GameService gameService;
    private final GameMapper gameMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = RabbitMqConfig.QUIZ_RESULTS_QUEUE)
    public void receiveGeneratedQuiz(QuizResultMessage message){
        log.info("RABBITMQ: Received quiz result for room {} with status {}", message.getRoomCode(), message.getStatus());

        try {
            if (STATUS_SUCCESS.equalsIgnoreCase(message.getStatus())) {
                GameRoom updatedRoom = gameService.processQuizResult(message);
                broadcastUpdatedRoom(updatedRoom);
                log.info("BROADCAST: Sent READY room state to room {}", updatedRoom.getRoomCode());
                return;
            }

            if (STATUS_FAILED.equalsIgnoreCase(message.getStatus())) {
                String errorMessage = message.getErrorMessage() == null
                        ? "Quiz generation failed. Please try again."
                        : message.getErrorMessage();

                GameRoom updatedRoom = gameService.handleQuizGenerationFailure(message.getRoomCode(), errorMessage);
                broadcastUpdatedRoom(updatedRoom);
                log.warn(
                        "Quiz generation failed for room {} with code {} and reason {}",
                        updatedRoom.getRoomCode(),
                        message.getErrorCode(),
                        errorMessage
                );
                messagingTemplate.convertAndSendToUser(
                        updatedRoom.getHost().getUsername(),
                        "/queue/errors",
                        GENERATION_UNAVAILABLE_MESSAGE
                );
                log.warn("BROADCAST: Sent FAILED generation notification for room {}", updatedRoom.getRoomCode());
                return;
            }

            log.warn("Ignoring quiz result with unsupported status '{}' for room {}", message.getStatus(), message.getRoomCode());

        } catch (Exception e) {
            log.warn("ERROR: Failed to process quiz results for room {}", message.getRoomCode(), e);
        }
    }

    private void broadcastUpdatedRoom(GameRoom updatedRoom) {
        GameRoomDto roomDto = gameMapper.toDto(updatedRoom);
        messagingTemplate.convertAndSend(
                "/topic/room/" + updatedRoom.getRoomCode(),
                roomDto
        );
    }
}

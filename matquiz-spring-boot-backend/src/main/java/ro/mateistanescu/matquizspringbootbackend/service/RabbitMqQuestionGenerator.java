package ro.mateistanescu.matquizspringbootbackend.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import ro.mateistanescu.matquizspringbootbackend.configuration.RabbitMqConfig;
import ro.mateistanescu.matquizspringbootbackend.dtos.QuizGenerationPayload;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.repository.GameRoomRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.QuestionRepository;

@Service
@RequiredArgsConstructor
@Primary
@Slf4j
public class RabbitMqQuestionGenerator implements QuestionGeneratorService {
    private final QuestionRepository questionRepository;
    private final GameRoomRepository gameRoomRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqConfig rabbitMqConfig;

    @Override
    @Transactional
    public void generateQuestions(GameRoom room) {

        //TODO: change the quiz identification to be by correlation_id
        //This service sends a message in the rabbitmq quiz generation queue as JSON Payload with
        //------roomCode, topic, difficulty
        //Updates the status of the room to GENERATING

        log.info("RABBITMQ: Requesting quiz generation for room: {}", room.getRoomCode());

        updateStatusOfRoom(room, GameStatus.GENERATING);

        QuizGenerationPayload payload = QuizGenerationPayload.builder()
                .roomCode(room.getRoomCode())
                .topic(room.getTopic())
                .difficulty(room.getDifficulty().name()) // Send Enum as String
                .build();

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.QUIZ_GENERATION_EXCHANGE,
                    RabbitMqConfig.QUIZ_GENERATION_ROUTING_KEY,
                    payload
            );
            log.info("RABBITMQ SENT: Payload sent to queue '{}'", RabbitMqConfig.QUIZ_GENERATION_QUEUE);
        } catch (AmqpException e) {
            log.error("RABBITMQ FAILED: Could not send message to RabbitMQ", e);
            updateStatusOfRoom(room, GameStatus.WAITING);
            throw new RuntimeException("Failed to request quiz generation", e);
        }
    }

    @Override
    @Transactional
    public void updateStatusOfRoom(GameRoom room, GameStatus status){
        room.setStatus(status);
        gameRoomRepository.save(room);
        log.info("Room {} status updated to {}", room.getRoomCode(), status);
    }
}

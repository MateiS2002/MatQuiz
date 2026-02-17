package ro.mateistanescu.matquizaiservicejava.consumer;

import com.openai.errors.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import ro.mateistanescu.matquizaiservicejava.configuration.RabbitMqConfig;
import ro.mateistanescu.matquizaiservicejava.dtos.QuizGenerationPayload;
import ro.mateistanescu.matquizaiservicejava.dtos.QuizGenerationResultDto;
import ro.mateistanescu.matquizaiservicejava.dtos.QuizResultSchemaDto;
import ro.mateistanescu.matquizaiservicejava.service.QuizGenerationService;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionRequestListener {
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final QuizGenerationService quizGenerationService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMqConfig.QUIZ_GENERATION_QUEUE)
    public void receiveQuestionGenerationRequest(QuizGenerationPayload payload){
        log.info("Received: Generation request for room {} with topic {} and difficulty {}",
                payload.getRoomCode(), payload.getTopic(), payload.getDifficulty());

        try{
            QuizResultSchemaDto validatedQuiz = quizGenerationService.generateQuiz(
                    payload.getRoomCode(),
                    payload.getTopic(),
                    payload.getDifficulty()
            );

            if(!validatedQuiz.roomCode().equals(payload.getRoomCode())){
                throw new IllegalArgumentException("Invalid room code in the validated quiz");
            }

            publishResult(new QuizGenerationResultDto(
                    payload.getRoomCode(),
                    STATUS_SUCCESS,
                    validatedQuiz.questions(),
                    null,
                    null
            ));
            log.info("Sent: Successful quiz result for room {}", payload.getRoomCode());
        } catch (Exception generationException) {
            String errorCode = resolveErrorCode(generationException);
            String errorMessage = resolveErrorMessage(generationException);
            log.warn(
                    "Quiz generation failed for room {} [{}]: {}",
                    payload.getRoomCode(),
                    errorCode,
                    errorMessage
            );
            publishFailure(payload.getRoomCode(), errorCode, errorMessage);
        }
    }

    private void publishFailure(String roomCode, String errorCode, String errorMessage) {
        try {
            publishResult(new QuizGenerationResultDto(
                    roomCode,
                    STATUS_FAILED,
                    Collections.emptyList(),
                    errorCode,
                    errorMessage
            ));
            log.info("Sent: Failed quiz result for room {}", roomCode);
        } catch (AmqpException publishException) {
            log.error("Failed to publish failure result for room {}", roomCode, publishException);
            throw publishException;
        }
    }

    private String resolveErrorCode(Exception generationException) {
        if (generationException instanceof RateLimitException) {
            return "AI_RATE_LIMIT";
        }
        return "GENERATION_FAILED";
    }

    private String resolveErrorMessage(Exception generationException) {
        if (generationException instanceof RateLimitException) {
            return "AI provider is currently rate-limited";
        }
        String message = generationException.getMessage();
        if (message == null || message.isBlank()) {
            return "Quiz generation failed";
        }
        return message;
    }

    private void publishResult(QuizGenerationResultDto result) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.QUIZ_RESULTS_EXCHANGE,
                RabbitMqConfig.QUIZ_RESULTS_ROUTING_KEY,
                result
        );
    }
}

package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class QuizGenerationPayload implements Serializable {
    private String roomCode;
    private String topic;
    private String difficulty;
}

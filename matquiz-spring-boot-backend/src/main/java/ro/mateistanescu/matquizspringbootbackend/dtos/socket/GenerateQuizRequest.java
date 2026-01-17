package ro.mateistanescu.matquizspringbootbackend.dtos.socket;

import lombok.Builder;
import lombok.Data;
import ro.mateistanescu.matquizspringbootbackend.enums.Difficulty;

@Data
@Builder
public class GenerateQuizRequest {
    private String roomCode;
    private String topic;
    private Difficulty difficulty;
}

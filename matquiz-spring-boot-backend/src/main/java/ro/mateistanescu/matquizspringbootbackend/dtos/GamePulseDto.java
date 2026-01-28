package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GamePulseDto {
    private String roomCode;
    private String status;
    private int currentQuestionIndex;
    private long remainingTimeMs;
    private long serverTime;
}

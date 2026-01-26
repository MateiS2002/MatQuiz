package ro.mateistanescu.matquizspringbootbackend.dtos.socket;

import lombok.Data;

@Data
public class CorrectAnswerRequest {
    private String roomCode;
    private Long questionId;
}

package ro.mateistanescu.matquizspringbootbackend.dtos.socket;

import lombok.Data;

@Data
public class QuestionRequest {
    private String roomCode;
}

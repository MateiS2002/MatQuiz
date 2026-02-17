package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Data;

import java.util.List;

@Data
public class QuizResultMessage {
    private String roomCode;
    private String status;
    private List<QuestionData> questions;
    private String errorCode;
    private String errorMessage;

    @Data
    public static class QuestionData {
        private String questionText;
        private List<String> answers;
        private int correctIndex;
    }
}

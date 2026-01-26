package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Data;

import java.util.List;

@Data
public class QuizResultMessage {
    private String roomCode;
    private List<QuestionData> questions;

    @Data
    public static class QuestionData {
        private String questionText;
        private List<String> answers;
        private int correctIndex;
    }
}

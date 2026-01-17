package ro.mateistanescu.matquizspringbootbackend.dtos.socket;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnswerSubmissionRequest {
    private String roomCode;
    private Long questionId;
    private Integer selectedAnswerIndex;// 0-based index of the selected answer
    private LocalDateTime submissionTime;
}
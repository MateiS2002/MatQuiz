package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnswerResultDto {
    private Long questionId;
    private boolean isCorrect;
    private Integer correctAnswerIndex;
    private Integer pointsEarned;
    private Integer newTotalScore;
}

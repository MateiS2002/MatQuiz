package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CorrectAnswerDto {
    private Long questionId;
    private Integer correctAnswer;
}

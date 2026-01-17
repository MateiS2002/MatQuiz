package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuestionDto {
    private Long questionId;
    private String question_text;
    private List<String> answers;
    private Integer order_index;
}

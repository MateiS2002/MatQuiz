package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AnswerProgressDto {
    private String nickname;
    private boolean answered;
}

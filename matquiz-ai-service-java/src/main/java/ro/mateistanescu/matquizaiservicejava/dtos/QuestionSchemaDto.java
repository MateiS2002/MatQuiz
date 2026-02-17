package ro.mateistanescu.matquizaiservicejava.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

public record QuestionSchemaDto(
        @JsonProperty(required = true, value = "questionText")
        @NotNull
        String questionText,

        @JsonProperty(required = true, value = "answers")
        @NotNull
        List<String> answers,

        @JsonProperty(required = true, value = "correctIndex")
        @Min(0)
        int correctIndex
) {}

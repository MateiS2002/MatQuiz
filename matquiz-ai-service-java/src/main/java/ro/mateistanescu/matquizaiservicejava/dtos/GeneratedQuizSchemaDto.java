package ro.mateistanescu.matquizaiservicejava.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

public record GeneratedQuizSchemaDto(
        @JsonProperty(required = true, value = "questions")
        @NotNull
        @Valid
        List<QuestionSchemaDto> questions
) {}

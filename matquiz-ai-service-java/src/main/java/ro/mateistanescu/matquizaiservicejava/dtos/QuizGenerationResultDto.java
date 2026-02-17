package ro.mateistanescu.matquizaiservicejava.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record QuizGenerationResultDto(
        @JsonProperty(required = true, value = "roomCode")
        String roomCode,

        @JsonProperty(required = true, value = "status")
        String status,

        @JsonProperty(value = "questions")
        List<QuestionSchemaDto> questions,

        @JsonProperty(value = "errorCode")
        String errorCode,

        @JsonProperty(value = "errorMessage")
        String errorMessage
) {}

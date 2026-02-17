package ro.mateistanescu.matquizaiservicejava.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.ai.openaisdk.OpenAiSdkChatOptions;
import org.springframework.stereotype.Service;
import ro.mateistanescu.matquizaiservicejava.dtos.GeneratedQuizSchemaDto;
import ro.mateistanescu.matquizaiservicejava.dtos.QuestionSchemaDto;
import ro.mateistanescu.matquizaiservicejava.dtos.QuizResultSchemaDto;

import java.util.List;

/**
 * Generates quizzes through the AI provider while preserving trusted backend ownership of roomCode.
 * The model is constrained to output only question data through a dedicated schema DTO, then the
 * service maps that output into the transport DTO used by downstream components.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizGenerationService {

    private static final int EXPECTED_QUESTION_COUNT = 5;
    private static final int EXPECTED_ANSWER_COUNT = 4;

    private final OpenAiSdkChatModel chatModel;

    public QuizResultSchemaDto generateQuiz(String roomCode, String topic, String difficulty) {

        var converter = new BeanOutputConverter<>(GeneratedQuizSchemaDto.class);

        OpenAiSdkChatOptions options = OpenAiSdkChatOptions.builder()
                .responseFormat(OpenAiSdkChatModel.ResponseFormat.builder()
                        .type(OpenAiSdkChatModel.ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema(converter.getJsonSchema())
                        .build())
                .build();

        String prompt =
                "You are a charismatic game-show host creating an online quiz. "
                        + "Topic: '" + topic + "'. "
                        + "Difficulty: '" + difficulty + "'. "
                        + "If the topic is unsafe, offensive, or unclear gibberish, replace it with one safe fun topic from: "
                        + "movies, world food, animals, sports, space, inventions. "
                        + "Create exactly 5 multiple-choice questions with 4 options each and one correct answer. "
                        + "Make them playful and witty (PG humor), challenging but fair, and suitable for a fast online game. "
                        + "Avoid obscure trivia, trick wording, or ambiguous answers. "
                        + "Use varied question styles (scenario, clue-based, elimination, comparison, pattern). "
                        + "Make distractors plausible, similar in length, and never use 'All of the above' or 'None of the above'. "
                        + "Progress difficulty naturally from question 1 to question 5.";
        Prompt chatPrompt = new Prompt(prompt, options);

        var response = chatModel.call(chatPrompt);

        Integer totalTokens = null;
        String model = null;
        if (response.getMetadata().getUsage() != null && response.getMetadata().getModel() != null) {
            totalTokens = response.getMetadata().getUsage().getTotalTokens();
            model = response.getMetadata().getModel();
        }
        log.info("AI usage for room {}: totalTokens= {} with model used: {}", roomCode, totalTokens, model);

        String json = response.getResult().getOutput().getText();

        if(json == null || json.isEmpty()){
            throw new IllegalStateException("AI returned empty JSON response");
        }

        GeneratedQuizSchemaDto aiResult = converter.convert(json);
        validateGeneratedQuiz(aiResult);

        return new QuizResultSchemaDto(roomCode, aiResult.questions());
    }

    private void validateGeneratedQuiz(GeneratedQuizSchemaDto quiz) {
        if (quiz == null || quiz.questions() == null || quiz.questions().size() != EXPECTED_QUESTION_COUNT) {
            throw new IllegalStateException("AI response must contain exactly 5 questions");
        }

        for (QuestionSchemaDto question : quiz.questions()) {
            List<String> answers = question.answers();
            if (answers == null || answers.size() != EXPECTED_ANSWER_COUNT) {
                throw new IllegalStateException("Each question must contain exactly 4 answers");
            }

            int correctIndex = question.correctIndex();
            if (correctIndex >= answers.size()) {
                throw new IllegalStateException("correctIndex is out of bounds for answers list");
            }
        }
    }
}

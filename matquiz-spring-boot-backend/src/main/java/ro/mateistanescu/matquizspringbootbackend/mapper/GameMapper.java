package ro.mateistanescu.matquizspringbootbackend.mapper;

import org.springframework.stereotype.Component;
import ro.mateistanescu.matquizspringbootbackend.dtos.*;
import ro.mateistanescu.matquizspringbootbackend.entity.GamePlayer;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.Question;

import java.util.stream.Collectors;

@Component
public class GameMapper {

    public GameRoomDto toDto(GameRoom room) {
        return GameRoomDto.builder()
                .roomCode(room.getRoomCode())
                .topic(room.getTopic())
                .difficulty(room.getDifficulty())
                .status(room.getStatus())
                .host(UserSummaryDto.builder()
                        .username(room.getHost().getUsername())
                        .avatarUrl(room.getHost().getAvatarUrl())
                        .build())
                .players(room.getPlayers().stream()
                        .map(this::toPlayerDto)
                        .collect(Collectors.toList()))
                .build();
    }

    public GamePlayerDto toPlayerDto(GamePlayer player) {
        return GamePlayerDto.builder()
                .nickname(player.getNickname())
                .score(player.getScore())
                .isConnected(player.getIsConnected())
                .avatarUrl(player.getUser().getAvatarUrl())
                .build();
    }

    public QuestionDto toQuestionDto(Question question) {
        return QuestionDto.builder()
                .questionId(question.getId())
                .question_text(question.getQuestionText())
                .answers(question.getAnswers())
                .order_index(question.getOrderIndex())
                .build();
    }

    public CorrectAnswerDto toCorrectAnswerDto(Question question) {
        return CorrectAnswerDto.builder()
                .questionId(question.getId())
                .correctAnswer(question.getCorrectIndex())
                .build();
    }
}

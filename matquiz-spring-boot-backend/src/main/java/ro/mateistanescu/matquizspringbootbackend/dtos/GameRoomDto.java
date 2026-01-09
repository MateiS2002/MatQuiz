package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Builder;
import lombok.Data;
import ro.mateistanescu.matquizspringbootbackend.enums.Difficulty;

import java.util.List;

@Data
@Builder
public class GameRoomDto {
    private String roomCode;
    private String topic;
    private Difficulty difficulty;
    private UserSummaryDto host;
    private List<GamePlayerDto> players;
}

package ro.mateistanescu.matquizspringbootbackend.mapper;

import org.springframework.stereotype.Component;
import ro.mateistanescu.matquizspringbootbackend.dtos.UserSummaryDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.GamePlayerDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.entity.GamePlayer;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;

import java.util.stream.Collectors;

@Component
public class GameMapper {

    public GameRoomDto toDto(GameRoom room) {
        return GameRoomDto.builder()
                .roomCode(room.getRoomCode())
                .topic(room.getTopic())
                .difficulty(room.getDifficulty())
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
}

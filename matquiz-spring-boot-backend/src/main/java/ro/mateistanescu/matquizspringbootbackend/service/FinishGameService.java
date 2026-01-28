package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.entity.GamePlayer;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;
import ro.mateistanescu.matquizspringbootbackend.repository.GameRoomRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinishGameService {
    private final GameRoomRepository gameRoomRepository;
    private final UserRepository userRepository;
    private final GameMapper gameMapper;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Finishes the game and updates user statistics.
     * Called automatically after the last question timeout.
     */
    @Transactional
    public void finishGame(String roomCode) {
        GameRoom room = gameRoomRepository.findWithDetailsByRoomCode(roomCode)
                .orElse(null);

        if (room == null) {
            log.warn("Room {} not found when trying to finish game", roomCode);
            return;
        }

        // Only finish if still playing (in case a game was already finished)
        if (room.getStatus() != GameStatus.PLAYING) {
            log.info("Room {} is not in PLAYING state, skipping finish", roomCode);
            return;
        }

        log.info("Finishing game for room {}", roomCode);

        // Set game status to finished
        room.setStatus(GameStatus.FINISHED);
        gameRoomRepository.save(room);

        // Update user statistics for all players
        for (GamePlayer player : room.getPlayers()) {
            User user = player.getUser();
            user.setTotalGamesPlayed(user.getTotalGamesPlayed() + 1);
            user.setLastGamePoints(player.getScore());
            userRepository.save(user);
            log.info("Updated stats for user {}: totalGames={}, lastGamePoints={}",
                    user.getUsername(), user.getTotalGamesPlayed(), user.getLastGamePoints());
        }

        // Broadcast final room state to all players
        GameRoomDto roomDto = gameMapper.toDto(room);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomCode,
                roomDto
        );

        log.info("Game finished for room {}. Final results broadcasted.", roomCode);
    }
}

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinishGameService {
    private static final int MIN_PLAYERS_FOR_ELO_REWARD = 2;
    private static final int MIN_ELO_RATING = 0;
    private static final int DEFAULT_ELO_RATING = 1000;
    private static final Map<Integer, List<Integer>> ELO_DELTAS_BY_PLAYER_COUNT = Map.of(
            1, List.of(0),
            2, List.of(10, -10),
            3, List.of(20, 10, -10),
            4, List.of(30, 20, 5, -10),
            5, List.of(50, 30, 10, -10, -20)
    );

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

        List<GamePlayer> players = room.getPlayers();
        Map<Long, Integer> eloDeltaByPlayerId = calculateEloDeltas(players);
        List<User> usersToSave = new ArrayList<>(players.size());

        // Update user statistics and ELO rating for all players
        for (GamePlayer player : players) {
            User user = player.getUser();
            int currentElo = user.getEloRating() == null ? DEFAULT_ELO_RATING : user.getEloRating();
            int eloDelta = eloDeltaByPlayerId.getOrDefault(player.getId(), 0);
            int updatedElo = Math.max(MIN_ELO_RATING, currentElo + eloDelta);

            user.setTotalGamesPlayed(user.getTotalGamesPlayed() + 1);
            user.setLastGamePoints(player.getScore());
            user.setEloRating(updatedElo);
            usersToSave.add(user);

            log.info("Updated stats for user {}: totalGames={}, lastGamePoints={}, eloDelta={}, eloRating={}",
                    user.getUsername(),
                    user.getTotalGamesPlayed(),
                    user.getLastGamePoints(),
                    eloDelta,
                    user.getEloRating());
        }
        userRepository.saveAll(usersToSave);

        // Broadcast final room state to all players
        GameRoomDto roomDto = gameMapper.toDto(room);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomCode,
                roomDto
        );

        log.info("Game finished for room {}. Final results broadcasted.", roomCode);
    }

    /**
     * Computes ELO deltas by player id using score-based placement.
     * Players with the same score share the same placement reward.
     */
    private Map<Long, Integer> calculateEloDeltas(List<GamePlayer> players) {
        Map<Long, Integer> eloDeltaByPlayerId = new HashMap<>();

        if (players.size() < MIN_PLAYERS_FOR_ELO_REWARD) {
            for (GamePlayer player : players) {
                eloDeltaByPlayerId.put(player.getId(), 0);
            }
            return eloDeltaByPlayerId;
        }

        List<GamePlayer> sortedPlayers = players.stream()
                .sorted(Comparator.comparing(GamePlayer::getScore).reversed()
                        .thenComparing(GamePlayer::getJoinedAt))
                .toList();

        int currentPlacement = 0;
        Integer previousScore = null;

        for (int index = 0; index < sortedPlayers.size(); index++) {
            GamePlayer currentPlayer = sortedPlayers.get(index);
            Integer currentScore = currentPlayer.getScore();

            if (previousScore == null || !previousScore.equals(currentScore)) {
                currentPlacement = index + 1;
            }

            int eloDelta = getEloDelta(sortedPlayers.size(), currentPlacement);
            eloDeltaByPlayerId.put(currentPlayer.getId(), eloDelta);

            previousScore = currentScore;
        }

        return eloDeltaByPlayerId;
    }

    private int getEloDelta(int playerCount, int placement) {
        List<Integer> deltas = ELO_DELTAS_BY_PLAYER_COUNT.get(playerCount);
        if (deltas == null || placement <= 0 || placement > deltas.size()) {
            return 0;
        }

        return deltas.get(placement - 1);
    }
}

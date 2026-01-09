package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mateistanescu.matquizspringbootbackend.entity.GamePlayer;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.Difficulty;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.repository.GamePlayerRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.GameRoomRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameRoomRepository gameRoomRepository;
    private final GamePlayerRepository gamePlayerRepository;

    private static final int MAX_PLAYERS = 5;

    /**
     * Creates a blank lobby for the host.
     * Topic and Difficulty will be set later when they click "Generate".
     */
    @Transactional
    public GameRoom createRoom(User host, String sessionID) {
        String roomCode = generateRoomCode();

        //TODO: This is a safety check because the room code generation algorithm must be changed
        if(roomCode.equals(gameRoomRepository.findByRoomCode(roomCode).map(GameRoom::getRoomCode).orElse(null))){
            throw new IllegalStateException("Room code already exists!");
        }

        //Build a "Blank" Room
        GameRoom room = GameRoom.builder()
                .roomCode(roomCode)
                .host(host)
                .topic("No input")       // Will be updated later
                .difficulty(Difficulty.EASY)  // Will be updated later
                .status(GameStatus.WAITING)
                .currentQuestionIndex(0)
                .createdAt(LocalDateTime.now())
                .players(new ArrayList<>())
                .build();

        room = gameRoomRepository.save(room);

        addPlayerToRoom(host, room, sessionID);

        log.info("Lobby created: {} by {}", roomCode, host.getUsername());

        //Extra query but ensures the entire room is fetched to avoid lazy loading issues
        return fetchFullRoom(roomCode);
    }

    @Transactional
    public GameRoom joinRoom(User user, String roomCode, String sessionID) {

        String normalizedRoomCode = roomCode.trim().toUpperCase();

        GameRoom room = gameRoomRepository.findByRoomCode(normalizedRoomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room " + normalizedRoomCode + " not found!"));

        if (room.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException("You cannot join a game that is already " + room.getStatus());
        }

        if (room.getPlayers().size() >= MAX_PLAYERS &&
                room.getPlayers().stream().noneMatch(p -> p.getUser().getId().equals(user.getId()))) {
            throw new IllegalStateException("Room is full! (Max " + MAX_PLAYERS + " players)");
        }

        GamePlayer existingPlayer = gamePlayerRepository.findByUserAndGameRoom(user, room).orElse(null);

        if (existingPlayer != null) {
            existingPlayer.setSocketSessionId(sessionID);
            existingPlayer.setIsConnected(true);
            gamePlayerRepository.save(existingPlayer);
            log.info("Player {} re-joined room {}", user.getUsername(), roomCode);
        } else {
            addPlayerToRoom(user, room, sessionID);
            log.info("Player {} joined room {}", user.getUsername(), roomCode);
        }

        return fetchFullRoom(roomCode);
    }

    @Transactional
    public GameRoom handleReconnect(String username, String newSessionId) {

        log.info("Searching active games for player {}", username);

        List<GamePlayer> activePlayers = gamePlayerRepository.findAllActiveGamesForUser(
                username,
                GameStatus.FINISHED //exclude finished games
        );

        if (activePlayers.isEmpty()) {
            log.info("No active games found for player {}", username);
            return null;
        }

        GamePlayer targetPlayer = activePlayers.getFirst();
        String currentSessionId = targetPlayer.getSocketSessionId();
        log.info("Found active game {} for player {}", targetPlayer.getGameRoom().getRoomCode(), username);

        if (newSessionId.equals(currentSessionId) && Boolean.TRUE.equals(targetPlayer.getIsConnected())) {
            return fetchFullRoom(targetPlayer.getGameRoom().getRoomCode());
        }

        // Update the session
        targetPlayer.setSocketSessionId(newSessionId);
        targetPlayer.setIsConnected(true);
        gamePlayerRepository.save(targetPlayer);

        log.info("Player {} reconnected to Active Room {}", username, targetPlayer.getGameRoom().getRoomCode());

        return fetchFullRoom(targetPlayer.getGameRoom().getRoomCode());
    }

    @Transactional
    public GameRoom handleDisconnect(String sessionId) {
        log.info("WebSocket Disconnect Event for session: {}", sessionId);

        GamePlayer player = gamePlayerRepository.findBySocketSessionId(sessionId)
                .orElse(null);

        if (player != null) {
            player.setIsConnected(false);
            gamePlayerRepository.save(player);

            log.info("Player {} (Host: {}) disconnected from room {}",
                    player.getNickname(),
                    player.getGameRoom().getHost().getUsername().equals(player.getUser().getUsername()),
                    player.getGameRoom().getRoomCode());

            return fetchFullRoom(player.getGameRoom().getRoomCode());
        } else {
            log.warn("No player found for disconnected session: {}", sessionId);
        }

        return null;
    }

    private void addPlayerToRoom(User user, GameRoom room, String sessionID) {
        GamePlayer player = GamePlayer.builder()
                .user(user)
                .gameRoom(room)
                .nickname(user.getUsername())
                .socketSessionId(sessionID)
                .score(0)
                .isConnected(true)
                .joinedAt(LocalDateTime.now())
                .build();

        gamePlayerRepository.save(player);

        room.addPlayer(player);
    }

    //TODO: This is just for testing purposes. Change the method to generate room number.
    private String generateRoomCode() {
        return RandomStringUtils.randomAlphanumeric(5).toUpperCase();
    }


    private GameRoom fetchFullRoom(String roomCode) {
        return gameRoomRepository.findWithDetailsByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalStateException("Room not found during fetch"));
    }
}
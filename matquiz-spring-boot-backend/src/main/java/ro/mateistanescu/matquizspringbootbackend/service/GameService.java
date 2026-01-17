package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.QuestionDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.AnswerSubmissionRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.GenerateQuizRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.QuestionRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.StartGameRequest;
import ro.mateistanescu.matquizspringbootbackend.entity.*;
import ro.mateistanescu.matquizspringbootbackend.enums.Difficulty;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;
import ro.mateistanescu.matquizspringbootbackend.repository.GamePlayerRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.GameRoomRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.PlayerAnswerRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.QuestionRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRoomRepository gameRoomRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final QuestionRepository questionRepository;
    private final PlayerAnswerRepository playerAnswerRepository;
    private final QuestionGeneratorService questionGeneratorService;
    private final GameMapper gameMapper;

    private static final int MAX_PLAYERS = 5;

    /**
     * Creates a blank lobby for the host.
     * Topic and Difficulty will be set later when they click "Generate".
     */
    @Transactional
    public GameRoom createRoom(User host, String sessionID) {
        // Remove host from any active rooms before creating a new one
        leaveAllActiveRooms(host);

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

        if (room.getStatus() == GameStatus.PLAYING || room.getStatus() == GameStatus.FINISHED) {
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
            leaveAllActiveRooms(user);
            addPlayerToRoom(user, room, sessionID);
            log.info("Player {} joined room {}", user.getUsername(), roomCode);
        }

        return fetchFullRoom(roomCode);
    }

    @Transactional
    public GameRoom startGame(User user, StartGameRequest request) {
        String roomCode = request.getRoomCode().trim().toUpperCase();

        GameRoom room = gameRoomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room " + roomCode + " not found!"));

        if(room.getStatus() != GameStatus.READY){
            throw new IllegalStateException("Game cannot be started because it is not in READY state!");
        }

        room.setStatus(GameStatus.PLAYING);
        gameRoomRepository.save(room);

        return fetchFullRoom(roomCode);
    }

    @Transactional
    public QuestionDto fetchQuestion(User user, QuestionRequest request) {
        String roomCode = request.getRoomCode().trim().toUpperCase();

        GameRoom room = gameRoomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room " + roomCode + " not found!"));


        if(room.getQuestions() == null || room.getQuestions().isEmpty()) {
            throw new IllegalStateException("No questions available for this room!");
        }

        if(room.getStatus() != GameStatus.PLAYING){
            throw new IllegalStateException("Questions cannot be fetched because the room is not in PLAYING state!");
        }

        if (!room.getHost().getId().equals(user.getId())) {
            throw new IllegalStateException("Only the host can request questions!");
        }

        int currentIndex = room.getCurrentQuestionIndex();

        if(currentIndex < 0 || currentIndex >= room.getQuestions().size()) {
            throw new IllegalStateException("Invalid question index: " + currentIndex);
        }

        Question question = room.getQuestions().get(currentIndex);

        int nextIndex = currentIndex + 1;

        if(nextIndex > room.getQuestions().size()) {
            log.info("All questions answered in room {}. Game finishing...", roomCode);
            room.setStatus(GameStatus.FINISHED);
        }

        room.setCurrentQuestionIndex(nextIndex);

        question.setPostedAt(LocalDateTime.now());
        questionRepository.save(question);
        gameRoomRepository.save(room);

        log.info("Fetched question {} of {} for room {}",
                currentIndex + 1, room.getQuestions().size(), roomCode);

        return gameMapper.toQuestionDto(question);
    }

    @Transactional
    public PlayerAnswer submitAnswer(User user, AnswerSubmissionRequest request) {
        String roomCode = request.getRoomCode().trim().toUpperCase();

        // Find the room
        GameRoom room = gameRoomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room " + roomCode + " not found!"));

        // Verify game is in progress
        if(room.getStatus() != GameStatus.PLAYING){
            throw new IllegalStateException("Cannot submit answers. Game is not in PLAYING state!");
        }

        // Find the player
        GamePlayer player = gamePlayerRepository.findByUserAndGameRoom(user, room)
                .orElseThrow(() -> new IllegalStateException("Player not found in this room!"));

        // Find the question
        Question question = room.getQuestions().stream()
                .filter(q -> q.getId().equals(request.getQuestionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question not found!"));

        // Validate answer index
        if(request.getSelectedAnswerIndex() < 0 ||
                request.getSelectedAnswerIndex() >= question.getAnswers().size()) {
            throw new IllegalArgumentException("Invalid answer index!");
        }

        // Check if the player already answered this question
        PlayerAnswer existingAnswer = playerAnswerRepository
                .findByGamePlayerAndQuestion(player, question)
                .orElse(null);

        if(existingAnswer != null) {
            throw new IllegalStateException("You have already answered this question!");
        }

        // Check if the answer is correct
        boolean isCorrect = request.getSelectedAnswerIndex().equals(question.getCorrectIndex());
        LocalDateTime clientSentRequestAt = request.getSubmissionTime();
        LocalDateTime questionPostedAt = question.getPostedAt();
        int timeTakenMs = 30000;

        if(clientSentRequestAt != null && questionPostedAt != null) {
            timeTakenMs = (int) Duration.between(questionPostedAt, clientSentRequestAt).toMillis();
        }

        if (timeTakenMs > 30000 || timeTakenMs < 0) {
            timeTakenMs = 30000;
        }

        // Calculate points
        //TODO : Finish the logic for time based awards
        int pointsEarned = isCorrect ? 100 : 0;

        // Create an answer record
        PlayerAnswer answer = PlayerAnswer.builder()
                .gamePlayer(player)
                .question(question)
                .selectedIndex(request.getSelectedAnswerIndex())
                .isCorrect(isCorrect)
                .pointsAwarded(pointsEarned)
                .timeTakenMs(timeTakenMs)
                .answeredAt(clientSentRequestAt)
                .build();

        playerAnswerRepository.save(answer);

        // Update player score
        player.setScore(player.getScore() + pointsEarned);
        gamePlayerRepository.save(player);

        log.info("Player {} answered question {} in room {}. Correct: {}, Points: {}",
                user.getUsername(), question.getId(), roomCode, isCorrect, pointsEarned);

        return answer;
    }


    @Transactional
    public GameRoom requestQuizGeneration(User user, GenerateQuizRequest request) {
        GameRoom room = gameRoomRepository.findByRoomCode(request.getRoomCode())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (!room.getHost().getId().equals(user.getId())) {
            throw new IllegalStateException("Only the host can generate the quiz!");
        }

        if (request.getTopic().isBlank()) {
            throw new IllegalArgumentException("Topic cannot be empty!");
        }

        if (request.getDifficulty() == null) {
            throw new IllegalArgumentException("Difficulty cannot be empty!");
        }

        if (request.getDifficulty() != Difficulty.EASY &&
                request.getDifficulty() != Difficulty.ADVANCED) {
            throw new IllegalArgumentException("Only EASY and ADVANCED difficulties are supported!");
        }

        if (room.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException("The quiz can be only generated when the room is waiting! Another quiz might have been generated already.");
        }

        room.setTopic(request.getTopic());
        room.setDifficulty(request.getDifficulty());
        gameRoomRepository.save(room);

        questionGeneratorService.generateQuestions(room);

        return fetchFullRoom(request.getRoomCode());
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

        List<GamePlayer> players = gamePlayerRepository.findBySocketSessionId(sessionId);

        if (!players.isEmpty()) {
            GameRoom room = null;
            
            for (GamePlayer player : players) {
                player.setIsConnected(false);
                gamePlayerRepository.save(player);

                log.info("Player {} (Host: {}) disconnected from room {}",
                        player.getNickname(),
                        player.getGameRoom().getHost().getUsername().equals(player.getUser().getUsername()),
                        player.getGameRoom().getRoomCode());

                room = player.getGameRoom();
            }
            
            if (room != null) {
                return fetchFullRoom(room.getRoomCode());
            }
        } else {
            log.warn("No player found for disconnected session: {}", sessionId);
        }

        return null;
    }

    @Transactional
    public GameRoom leaveRoom(User user, String roomCode) {
        String normalizedRoomCode = roomCode.trim().toUpperCase();

        GameRoom room = gameRoomRepository.findByRoomCode(normalizedRoomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room " + normalizedRoomCode + " not found!"));

        GamePlayer playerToRemove = gamePlayerRepository.findByUserAndGameRoom(user, room)
                .orElseThrow(() -> new IllegalStateException("Player is not in this room!"));

        // Check if this player is the host
        boolean isHost = room.getHost().getId().equals(user.getId());

        // Remove the player from the room
        room.getPlayers().remove(playerToRemove);
        gamePlayerRepository.delete(playerToRemove);

        log.info("Player {} left room {}", user.getUsername(), roomCode);

        // If the host left, handle host transfer or room deletion
        if (isHost) {
            if (room.getPlayers().isEmpty()) {
                // No players left, delete the room entirely
                log.info("Host left and room {} is empty. Deleting room.", roomCode);
                gameRoomRepository.delete(room);
                return null; // Room no longer exists
            } else {
                // Transfer host to the next player
                GamePlayer newHost = room.getPlayers().get(0);
                room.setHost(newHost.getUser());
                gameRoomRepository.save(room);
                log.info("Host transferred to {} in room {}", newHost.getUser().getUsername(), roomCode);
            }
        }

        // If room still exists and has players, return updated room
        if (!room.getPlayers().isEmpty()) {
            return fetchFullRoom(roomCode);
        } else {
            // Last player left (non-host case)
            log.info("Room {} is now empty. Deleting room.", roomCode);
            gameRoomRepository.delete(room);
            return null;
        }
    }

    /**
     * Removes a player from any active rooms they might be in.
     * This should be called before a player creates or joins a new room.
     */
    @Transactional
    public void leaveAllActiveRooms(User user) {
        log.info("Removing player {} from all active rooms", user.getUsername());

        List<GamePlayer> activePlayers = gamePlayerRepository.findAllActiveGamesForUser(
                user.getUsername(),
                GameStatus.FINISHED // exclude finished games
        );

        for (GamePlayer player : activePlayers) {
            GameRoom room = player.getGameRoom();
            String roomCode = room.getRoomCode();

            log.info("Removing player {} from room {}", user.getUsername(), roomCode);

            // Delete player from database first
            gamePlayerRepository.delete(player);

            // Refresh room to get updated player list
            gameRoomRepository.flush();

            // Handle host transfer or room deletion
            boolean isHost = room.getHost().getId().equals(user.getId());

            if (isHost) {
                if (room.getPlayers().isEmpty()) {
                    log.info("Host left and room {} is empty. Deleting room", roomCode);
                    gameRoomRepository.delete(room);
                } else {
                    GamePlayer newHost = room.getPlayers().get(0);
                    room.setHost(newHost.getUser());
                    gameRoomRepository.save(room);
                    log.info("Host transferred to {} in room {}.", newHost.getUser().getUsername(), roomCode);
                }
            } else if (room.getPlayers().isEmpty()) {
                log.info("Room {} is now empty. Deleting room", roomCode);
                gameRoomRepository.delete(room);
            }
        }
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


    public GameRoom fetchFullRoom(String roomCode) {
        return gameRoomRepository.findWithDetailsByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalStateException("Room not found during fetch"));
    }
}
package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mateistanescu.matquizspringbootbackend.dtos.*;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.*;
import ro.mateistanescu.matquizspringbootbackend.entity.*;
import ro.mateistanescu.matquizspringbootbackend.enums.Difficulty;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;
import ro.mateistanescu.matquizspringbootbackend.repository.GamePlayerRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.GameRoomRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.PlayerAnswerRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.QuestionRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameRoomRepository gameRoomRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final QuestionRepository questionRepository;
    private final PlayerAnswerRepository playerAnswerRepository;
    private final QuestionGeneratorService questionGeneratorService;
    private final GameMapper gameMapper;
    private final TaskScheduler taskScheduler;
    private final FinishGameService finishGameService;
    private final FailedAnswerService failedAnswerService;
    private final SimpMessagingTemplate messagingTemplate;
    private final QuizTimeoutService quizTimeoutService;

    private static final int MAX_PLAYERS = 5;
    private static final long GAME_FINISH_TIMEOUT_MS = 35000; // 35 seconds: 30s question time + 5s buffer
    private static final long QUIZ_GENERATION_TIMEOUT = 45000; // 45 seconds still needs some optimization


    /**
     * Creates a blank lobby for the host.
     * Topic and Difficulty will be set later when they click "Generate".
     */
    @Transactional
    public GameRoom createRoom(User host, String sessionID) {
        // Remove host from any active rooms before creating a new one
        leaveAllActiveRooms(host);

        String roomCode = generateRoomCode();

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

        if (!room.getHost().getId().equals(user.getId())) {
            throw new IllegalStateException("Only the host can start game!");
        }

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
        boolean isLastQuestion = nextIndex >= room.getQuestions().size();

        room.setCurrentQuestionIndex(nextIndex);

        // Set the start time for server-side timing
        LocalDateTime now = LocalDateTime.now();
        question.setPostedAt(now);
        room.setQuestionStartedAt(now);

        questionRepository.save(question);
        gameRoomRepository.save(room);

        log.info("Fetched question {} of {} for room {}", nextIndex, room.getQuestions().size(), roomCode);

        // TRIGGER: Schedule automatic reveal after 30 seconds + buffer
        Long questionId = question.getId();
        taskScheduler.schedule(() -> {
            try {
                processReveal(roomCode, questionId);
            } catch (Exception e) {
                log.warn("Timed reveal failed for room {} question {}", roomCode, questionId, e);
            }
        }, Instant.now().plusMillis(32000));

        if (isLastQuestion) {
            scheduleGameFinish(roomCode);
        }

        return gameMapper.toQuestionDto(question);
    }

    @Transactional
    public void submitAnswer(User user, AnswerSubmissionRequest request, LocalDateTime now) {
        String roomCode = request.getRoomCode().trim().toUpperCase();

        // Find the room
        GameRoom room = gameRoomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room " + roomCode + " not found!"));

        // Verify the game is in progress
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

        //Server side timing for security
        int timeTakenMs = 30000; //Default is max
        if (question.getPostedAt() != null) {
            timeTakenMs = (int) Duration.between(question.getPostedAt(), now).toMillis();
        }

        timeTakenMs = Math.clamp(timeTakenMs, 0, 30000);

        boolean isCorrect = request.getSelectedAnswerIndex().equals(question.getCorrectIndex());
        int pointsEarned = 0; //Default is min
        if (isCorrect) {
            //For example, a 20-sec answer has a factor of 0.33. A 3-sec answer has a factor of 0.9
            double speedFactor = (30000.0 - timeTakenMs) / 30000.0;
            pointsEarned = (int) (50 + (50 * speedFactor)); // Max 100, Min 50 for a correct answer
        }

        // Create an answer record
        PlayerAnswer answer = PlayerAnswer.builder()
                .gamePlayer(player)
                .question(question)
                .selectedIndex(request.getSelectedAnswerIndex())
                .isCorrect(isCorrect)
                .pointsAwarded(pointsEarned)
                .timeTakenMs(timeTakenMs)
                .answeredAt(now)
                .build();

        playerAnswerRepository.save(answer);

        // Update player score
        player.setScore(player.getScore() + pointsEarned);
        gamePlayerRepository.save(player);

        log.info("Player {} answered question {} in room {}. Correct: {}, Points: {}",
                user.getUsername(), question.getId(), roomCode, isCorrect, pointsEarned);

        // Check if this was the last answer needed
        long totalPlayers = room.getPlayers().size();
        long currentAnswers = playerAnswerRepository.countByQuestion(question);

        if (currentAnswers >= totalPlayers) {
            log.info("All players answered. Triggering immediate reveal for room {}", roomCode);
            processReveal(roomCode, question.getId());
        }
    }

    @Transactional
    public void processReveal(String roomCode, Long questionId) {
        GameRoom room = fetchFullRoom(roomCode);

        LocalDateTime now = LocalDateTime.now();

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        if (room.getStatus() != GameStatus.PLAYING) {
            return;
        }

        // IDEMPOTENCY CHECK: If the room has already been revealed.
        if (question.getRevealedAt() != null) {
            return;
        }

        question.setRevealedAt(now);
        questionRepository.save(question);

        List<PlayerAnswer> existingAnswers =
                playerAnswerRepository.findByQuestionAndGameRoom(question, room);

        // 1. Process "Missed" players (Assign 0 points)
        List<GamePlayer> playersWithoutAnswers = room.getPlayers().stream()
                .filter(player -> existingAnswers.stream()
                        .noneMatch(ans -> ans.getGamePlayer().getId().equals(player.getId())))
                .toList();

        for (GamePlayer player : playersWithoutAnswers) {
            PlayerAnswer missedAnswer = PlayerAnswer.builder()
                    .gamePlayer(player)
                    .question(question)
                    .isCorrect(false)
                    .pointsAwarded(0)
                    .timeTakenMs(30000)
                    .answeredAt(LocalDateTime.now())
                    .build();
            playerAnswerRepository.save(missedAnswer);

            // Notify the specific player they missed it
            failedAnswerService.sendFailedAnswerMessageToUser(player.getUser().getId());
        }

        // 2. BROADCAST REVEAL: Send the correct answer data
        CorrectAnswerDto revealDto = gameMapper.toCorrectAnswerDto(question);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/reveal", revealDto);

        // 3. BROADCAST UPDATE: Send the full RoomDto for final score synchronization
        GameRoomDto roomDto = gameMapper.toDto(fetchFullRoom(roomCode));
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, roomDto);

        log.info("Reveal executed for room {}", roomCode);

        int totalQuestions = room.getQuestionCount();
        if (room.getCurrentQuestionIndex() >= totalQuestions) {
            log.info("Final question revealed. Ending game immediately for room {}", roomCode);
            finishGameService.finishGame(roomCode);
        }
    }

    @Transactional
    public ResultsDto fetchRoomResults(User user, ResultsRequest request) {
        GameRoom room = gameRoomRepository.findByRoomCode(request.getRoomCode())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (!room.getHost().getId().equals(user.getId())) {
            throw new IllegalStateException("Only the host can request room results!");
        }

        List<GamePlayer> players = room.getPlayers();

        List<GamePlayerDto> playerDtos = players.stream()
                .map(player -> GamePlayerDto.builder()
                        .nickname(player.getNickname())
                        .score(player.getScore())
                        .isConnected(player.getIsConnected())
                        .avatarUrl(player.getUser().getAvatarUrl())
                        .build())
                .toList();

        return ResultsDto.builder()
                .endTime(LocalDateTime.now())
                .players(playerDtos)
                .build();
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

        if (room.getStatus() != GameStatus.WAITING && room.getStatus() != GameStatus.GENERATING) {
            throw new IllegalStateException("The quiz can be only generated when the room is waiting or generating!");
        }

        if (request.getTopic().length() > 30) {
            throw new IllegalArgumentException("Topic cannot be longer than 30 characters!");
        }

        scheduleQuizGenerationCheck(request.getRoomCode());

        room.setTopic(request.getTopic());
        room.setDifficulty(request.getDifficulty());
        gameRoomRepository.save(room);

        questionGeneratorService.generateQuestions(room);

        return fetchFullRoom(request.getRoomCode());
    }

    @Transactional
    public GameRoom processQuizResult(QuizResultMessage message) {
        GameRoom room = gameRoomRepository.findByRoomCode(message.getRoomCode())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if(room.getStatus() != GameStatus.GENERATING) {
            throw new IllegalStateException("The quiz can only be updated when the room is in GENERATING state!");
        }

        //if the quiz has been regenerated, for safety we delete the old questions for that room
        questionRepository.deleteAllByGameRoomId(room.getId());

        List<Question> questionEntities = new ArrayList<>();
        for (int i = 0; i < message.getQuestions().size(); i++) {
            QuizResultMessage.QuestionData data = message.getQuestions().get(i);

            Question question = Question.builder()
                    .gameRoom(room)
                    .questionText(data.getQuestionText())
                    .answers(data.getAnswers())
                    .correctIndex(data.getCorrectIndex())
                    .orderIndex(i + 1)
                    .build();

            questionEntities.add(question);
        }

        questionRepository.saveAll(questionEntities);

        room.setStatus(GameStatus.READY);

        log.info("Successfully processed AI results: {} questions added to room {}",
                questionEntities.size(), room.getRoomCode());

        return fetchFullRoom(room.getRoomCode());
    }

    @Transactional
    public GameRoom handleQuizGenerationFailure(String roomCode, String errorMessage) {
        GameRoom room = gameRoomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (room.getStatus() != GameStatus.GENERATING) {
            throw new IllegalStateException("The room can only fail generation from GENERATING state!");
        }

        room.setStatus(GameStatus.WAITING);
        gameRoomRepository.save(room);

        log.warn("Quiz generation failed for room {}: {}", roomCode, errorMessage);

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
    public boolean hasActiveGame(String username) {
        return !gamePlayerRepository.findAllActiveGamesForUser(username, GameStatus.FINISHED).isEmpty();
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

        if (room.getStatus() == GameStatus.FINISHED) {
            return null;
        }

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
                GamePlayer newHost = room.getPlayers().getFirst();
                room.setHost(newHost.getUser());
                gameRoomRepository.save(room);
                log.info("Host transferred to {} in room {}", newHost.getUser().getUsername(), roomCode);
            }
        }

        // If a room still exists and has players, return the updated room
        if (!room.getPlayers().isEmpty()) {
            return fetchFullRoom(roomCode);
        } else {
            // Last player left (non-host case)
            log.info("Room {} is now empty. Deleting room.", roomCode);
            gameRoomRepository.delete(room);
            return null;
        }
    }

    @Transactional
    public void endGameEarly(User user, EndGameEarlyRequest request) {
        GameRoom room = gameRoomRepository.findByRoomCode(request.getRoomCode())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if(!room.getHost().getId().equals(user.getId())) {
            throw new IllegalStateException("Only the host can end the game early!");
        }

        if (room.getStatus() == GameStatus.FINISHED) {
            throw new IllegalStateException("Game is already finished!");
        }

        room.setStatus(GameStatus.FINISHED);

        log.info("Game ended early for room {}", room.getRoomCode());

        gameRoomRepository.save(room);
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

            // Refresh room to get an updated player list
            gameRoomRepository.flush();

            // Handle host transfer or room deletion
            boolean isHost = room.getHost().getId().equals(user.getId());

            if (isHost) {
                if (room.getPlayers().isEmpty()) {
                    log.info("Host left and room {} is empty. Deleting room", roomCode);
                    gameRoomRepository.delete(room);
                } else {
                    GamePlayer newHost = room.getPlayers().getFirst();
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

    private String generateRoomCode() {
        return RandomStringUtils.secureStrong().nextAlphanumeric(5).toUpperCase();
    }

    public GameRoom fetchFullRoom(String roomCode) {
        return gameRoomRepository.findWithDetailsByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalStateException("Room not found during fetch"));
    }


    private void scheduleQuizGenerationCheck(String roomCode) {
        Instant scheduledTime = Instant.now().plusMillis(QUIZ_GENERATION_TIMEOUT);
        taskScheduler.schedule(() -> quizTimeoutService.quizTimeoutCheck(roomCode), scheduledTime);
        log.info("Scheduled quiz generation check for room {}", roomCode);
    }

    /**
     * Schedules the game to finish after a timeout (35 seconds).
     * This is called when the last question is fetched to allow players
     * time to answer with a network delay buffer.
     */
    private void scheduleGameFinish(String originalRoomCode) {
        log.info("Scheduling game finish for room {} in {} ms", originalRoomCode, GAME_FINISH_TIMEOUT_MS);

        Instant scheduledTime = Instant.now().plusMillis(GAME_FINISH_TIMEOUT_MS);
        taskScheduler.schedule(() -> finishGameService.finishGame(originalRoomCode), scheduledTime);
    }
}
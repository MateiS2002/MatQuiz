package ro.mateistanescu.matquizspringbootbackend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import ro.mateistanescu.matquizspringbootbackend.dtos.AnswerResultDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.QuestionDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.*;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.PlayerAnswer;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;
import ro.mateistanescu.matquizspringbootbackend.service.GameService;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GameSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameService gameService;
    private final GameMapper gameMapper;

    /**
     * 1. CREATE LOBBY
     * Client sends: /app/create
     * Server returns: a room object
     */
    @MessageMapping("/create")
    public void createRoom(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        User host = getUser(principal);
        String sessionID = headerAccessor.getSessionId();

        try {
            GameRoom room = gameService.createRoom(host, sessionID);

            GameRoomDto roomDto = gameMapper.toDto(room);

            //sends the full room object to the host
            messagingTemplate.convertAndSendToUser(
                    host.getUsername(),
                    "/queue/created",
                    roomDto
            );
        } catch (Exception e) {
            sendError(host.getUsername(), "Failed to create room: " + e.getMessage());
        }
    }

    /**
     * 2. JOIN LOBBY
     * Client sends: /app/join { "roomCode": "ABC123" }
     * Server returns: The full Room object for the client to render and a general message to all other players
     */
    @MessageMapping("/join")
    public void joinRoom(@Payload JoinRoomRequest request, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        User user = getUser(principal);
        String sessionID = headerAccessor.getSessionId();

        try {
            String roomCode = request.getRoomCode().trim().toUpperCase();
            GameRoom room = gameService.joinRoom(user, roomCode, sessionID);

            GameRoomDto roomDto = gameMapper.toDto(room);

            messagingTemplate.convertAndSendToUser(
                    user.getUsername(),
                    "/queue/joined",
                    roomDto
            );

            messagingTemplate.convertAndSend(
                    "/topic/room/" + room.getRoomCode(),
                    roomDto
            );

        } catch (Exception e) {
            sendError(user.getUsername(), e.getMessage());
        }
    }

    /**
     * 3. RECONNECT (Manual Trigger)
     * Client sends: /app/reconnect
     * Server returns: The Room DTO (if active game exists)
     */
    @MessageMapping("/reconnect")
    public void reconnect(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        if (principal == null) return;
        User user = getUser(principal);
        String sessionID = headerAccessor.getSessionId();

        try {
            GameRoom room = gameService.handleReconnect(user.getUsername(), sessionID);

            if (room != null) {
                GameRoomDto roomDto = gameMapper.toDto(room);

                messagingTemplate.convertAndSendToUser(
                        user.getUsername(),
                        "/queue/reconnected",
                        roomDto
                );

                // Notify others that the player is back
                messagingTemplate.convertAndSend(
                        "/topic/room/" + room.getRoomCode(),
                        roomDto
                );
            }
        } catch (Exception e) {
            log.error("Reconnect failed for user {}", user.getUsername(), e);
            sendError(user.getUsername(), "Reconnect failed: " + e.getMessage());
        }
    }

    /**
     * 4. LEAVE ROOM
     * Client sends: /app/leave { "roomCode": "ABC123" }
     * Server returns: The Room DTO (if an active game exists)
     */
    @MessageMapping("/leave")
    public void leaveRoom(@Payload LeaveRoomRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        String roomCode = request.getRoomCode().trim().toUpperCase();

        GameRoom room = gameService.leaveRoom(user, roomCode);

        if(room != null){
            GameRoomDto roomDto = gameMapper.toDto(room);

            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode,
                    roomDto
            );
        }

        messagingTemplate.convertAndSendToUser(
                user.getUsername(),
                "/queue/left",
                roomCode
        );
    }

    /**
     * 5. GENERATE QUIZ
     * Client sends: /app/generate
     * Payload: { "roomCode": "ABC", "topic": "Math", "difficulty": "EASY" }
     * Server returns: The Room DTO to update UI
     */
    @MessageMapping("/generate")
    public void generateQuiz(@Payload GenerateQuizRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        try {
            GameRoom room = gameService.requestQuizGeneration(user, request);

            if(room != null){
                GameRoomDto roomDto = gameMapper.toDto(room);

                messagingTemplate.convertAndSend(
                        "/topic/room/" + request.getRoomCode(),
                        roomDto
                        );

                log.info("Quiz generated for room {}", request.getRoomCode());
            } else{
            log.error("Quiz generation failed for room {}", request.getRoomCode());
            }
        } catch (Exception e) {
            sendError(user.getUsername(), "Generation failed: " + e.getMessage());
        }
    }


    /**
     * 6. START GAME
     * Client sends: /app/start { "roomCode": "ABC123" }
     * Server returns: The Room DTO to update UI
     */
    @MessageMapping("/startGame")
    public void startQuiz(@Payload StartGameRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        try {
            GameRoom room = gameService.startGame(request);

            if(room != null){
                GameRoomDto roomDto = gameMapper.toDto(room);

                //Broadcast new status of the room to all players
                messagingTemplate.convertAndSend(
                        "/topic/room/" + request.getRoomCode(),
                        roomDto
                        );
            }

        } catch (Exception e) {
            sendError(user.getUsername(), "Game start failed: " + e.getMessage());
        }
    }

    /**
     * 7. REQUEST QUESTION
     * Client sends: /app/requestQuestion { "roomCode": "ABC123" }
     * Server returns: The Question DTO
     * Client MUST be Host Of The Room
     */
    @MessageMapping("/requestQuestion")
    public void requestQuestion(@Payload QuestionRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        try {
            QuestionDto questionDto = gameService.fetchQuestion(user, request);

            // 1. Send the Question to everyone
            messagingTemplate.convertAndSend(
                    "/topic/room/" + request.getRoomCode().trim().toUpperCase(),
                    questionDto);

            // 2. If the game just finished, broadcast the final room state (to show results)
            GameRoom room = gameService.fetchFullRoom(request.getRoomCode().trim().toUpperCase());
            if (room.getStatus() == GameStatus.FINISHED) {
                messagingTemplate.convertAndSend(
                        "/topic/room/" + room.getRoomCode(),
                        gameMapper.toDto(room)
                );
            }

        } catch (Exception e){
            sendError(user.getUsername(), "Question request failed: " + e.getMessage());
        }
    }

    /**
     * 8. SUBMIT ANSWER
     *
     */
    @MessageMapping("/submitAnswer")
    public void submitAnswer(@Payload AnswerSubmissionRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);
        LocalDateTime clientSentRequestAt = LocalDateTime.now();

        try {
            PlayerAnswer answer = gameService.submitAnswer(user, request, clientSentRequestAt);

            // Send a result only to the player who answered
            AnswerResultDto result = AnswerResultDto.builder()
                    .questionId(answer.getQuestion().getId())
                    .isCorrect(answer.getIsCorrect())
                    .correctAnswerIndex(answer.getQuestion().getCorrectIndex())
                    .pointsEarned(answer.getPointsAwarded())
                    .newTotalScore(answer.getGamePlayer().getScore())
                    .build();

            messagingTemplate.convertAndSendToUser(
                    user.getUsername(),
                    "/queue/answerResult",
                    result
            );

            // Broadcast updated room state to all players (so they see updated scores)
            GameRoom room = gameService.fetchFullRoom(request.getRoomCode().trim().toUpperCase());
            GameRoomDto roomDto = gameMapper.toDto(room);

            messagingTemplate.convertAndSend(
                    "/topic/room/" + request.getRoomCode().trim().toUpperCase(),
                    roomDto
            );

            log.info("Answer processed for player {} in room {}", user.getUsername(), request.getRoomCode());

        } catch (Exception e){
            log.error("Answer submission failed: {}", e.getMessage());
            sendError(user.getUsername(), "Answer submission failed: " + e.getMessage());
        }
    }


    private User getUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            return (User) auth.getPrincipal();
        }
        throw new IllegalStateException("User not authenticated");
    }

    private void sendError(String username, String message) {
        log.error("Error for user {}: {}", username, message);
        messagingTemplate.convertAndSendToUser(username, "/queue/errors", message);
    }
}

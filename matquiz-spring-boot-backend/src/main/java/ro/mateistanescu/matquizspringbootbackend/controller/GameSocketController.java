package ro.mateistanescu.matquizspringbootbackend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import ro.mateistanescu.matquizspringbootbackend.dtos.*;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.*;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;
import ro.mateistanescu.matquizspringbootbackend.service.GameService;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
@Validated
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
    public void joinRoom(@Payload @Valid JoinRoomRequest request, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        User user = getUser(principal);
        String sessionID = headerAccessor.getSessionId();

        try {
            String roomCode = requireRoomCode(request.getRoomCode());
            request.setRoomCode(roomCode);
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
            sendError(user.getUsername(), resolveClientMessage(e, "Unable to join room."));
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
            sendError(user.getUsername(), "Reconnect failed: " + resolveClientMessage(e, "Unexpected server error."));
        }
    }

    /**
     * 4. LEAVE ROOM
     * Client sends: /app/leave { "roomCode": "ABC123" }
     * Server returns: The Room DTO (if an active game exists)
     */
    @MessageMapping("/leave")
    public void leaveRoom(@Payload @Valid LeaveRoomRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        try {
            String roomCode = requireRoomCode(request.getRoomCode());
            request.setRoomCode(roomCode);
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
        } catch (Exception e) {
            sendError(user.getUsername(), "Leave room failed: " + resolveClientMessage(e, "Unexpected server error."));
        }
    }

//    /**
//     * 5. KICK A PLAYER
//     *
//     */
//    @MessageMapping("/kick")
//    public void kickPlayer(Principal principal) {
//        User user = getUser(principal);
//
//        //TODO: Decide if i need this endpoint, for the mvp this is not necessary
//    }

    /**
     * 6. END GAME EARLY
     *
     */
    @MessageMapping("/endGame")
    public void endGame(@Payload @Valid EndGameEarlyRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        try{
            String roomCode = requireRoomCode(request.getRoomCode());
            request.setRoomCode(roomCode);
            // Broadcast END_GAME_EARLY message BEFORE finishing
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode,
                    "END_GAME_EARLY"
            );

            gameService.endGameEarly(user, request);

        } catch (Exception e){
            sendError(user.getUsername(), "End game failed: " + resolveClientMessage(e, "Unexpected server error."));
        }
    }


    /**
     * 7. GENERATE QUIZ
     * Client sends: /app/generate
     * Payload: { "roomCode": "ABC", "topic": "Math", "difficulty": "EASY" }
     * Server returns: The Room DTO to update UI
     */
    @MessageMapping("/generate")
    public void generateQuiz(@Payload @Valid GenerateQuizRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        try {
            String roomCode = requireRoomCode(request.getRoomCode());
            request.setRoomCode(roomCode);
            GameRoom room = gameService.requestQuizGeneration(user, request);

            if(room != null){
                GameRoomDto roomDto = gameMapper.toDto(room);

                messagingTemplate.convertAndSend(
                        "/topic/room/" + roomCode,
                        roomDto
                        );

                log.info("Quiz generating for room {}", roomCode);
            } else{
            log.error("Quiz generation failed for room {}", roomCode);
            }
        } catch (Exception e) {
            sendError(user.getUsername(), "Generation failed: " + resolveClientMessage(e, "Unexpected server error."));
        }
    }

    // THE UPDATED ROOM WITH STATUS READY IS SENT OUT WITH WEBSOCKET IN QuizResultListener

    /**
     * 8. START GAME
     * Client sends: /app/start { "roomCode": "ABC123" }
     * Server returns: The Room DTO to update UI
     */
    @MessageMapping("/startGame")
    public void startQuiz(@Payload @Valid StartGameRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        try {
            String roomCode = requireRoomCode(request.getRoomCode());
            request.setRoomCode(roomCode);
            GameRoom room = gameService.startGame(user, request);

            if(room != null){
                GameRoomDto roomDto = gameMapper.toDto(room);

                //Broadcast new status of the room to all players
                messagingTemplate.convertAndSend(
                        "/topic/room/" + roomCode,
                        roomDto
                        );
            }

        } catch (Exception e) {
            sendError(user.getUsername(), "Game start failed: " + resolveClientMessage(e, "Unexpected server error."));
        }
    }

    /**
     * 9. REQUEST QUESTION
     * Client sends: /app/requestQuestion { "roomCode": "ABC123" }
     * Server returns: The Question DTO
     * Client MUST be Host Of The Room
     */
    @MessageMapping("/requestQuestion")
    public void requestQuestion(@Payload @Valid QuestionRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        try {
            String roomCode = requireRoomCode(request.getRoomCode());
            request.setRoomCode(roomCode);
            QuestionDto questionDto = gameService.fetchQuestion(user, request);

            // 1. Send the Question to everyone
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode,
                    questionDto);

            // 2. If the game just finished, broadcast the final room state (to show results)
            GameRoom room = gameService.fetchFullRoom(roomCode);
            if (room.getStatus() == GameStatus.FINISHED) {
                messagingTemplate.convertAndSend(
                        "/topic/room/" + room.getRoomCode(),
                        gameMapper.toDto(room)
                );
            }

        } catch (Exception e){
            sendError(user.getUsername(), "Question request failed: " + resolveClientMessage(e, "Unexpected server error."));
        }
    }

    /**
     * 8. SUBMIT ANSWER
     */
    @MessageMapping("/submitAnswer")
    public void submitAnswer(@Payload @Valid AnswerSubmissionRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);
        LocalDateTime now = LocalDateTime.now();

        try {
            String roomCode = requireRoomCode(request.getRoomCode());
            requireQuestionId(request.getQuestionId());
            requireSelectedAnswerIndex(request.getSelectedAnswerIndex());
            request.setRoomCode(roomCode);
            gameService.submitAnswer(user, request, now);

            // Notify only the sender that their submission was successful and do not reveal the correct answer yet
            messagingTemplate.convertAndSendToUser(
                    user.getUsername(),
                    "/queue/submitAck", // New endpoint for simple "Success"
                    "OK"
            );

            // BROADCAST PROGRESS: Everyone sees that this user is "Done"
            AnswerProgressDto progress = new AnswerProgressDto(user.getUsername(), true);
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode + "/progress",
                    progress
            );

            log.info("Answer processed for player {} in room {}", user.getUsername(), roomCode);

        } catch (Exception e){
            log.error("Answer submission failed: {}", e.getMessage());
            sendError(user.getUsername(), "Answer submission failed: " + resolveClientMessage(e, "Unexpected server error."));
        }
    }


    /**
     * 9. REQUEST END GAME RESULTS
     */
    @MessageMapping("/endResults")
    public void endResults(@Payload @Valid ResultsRequest request, Principal principal) {
        if(principal == null) return;
        User user = getUser(principal);

        try{
            String roomCode = requireRoomCode(request.getRoomCode());
            request.setRoomCode(roomCode);
            ResultsDto resultsDto = gameService.fetchRoomResults(user, request);

            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode,
                    resultsDto
            );

            log.info("End game results broadcasted for room {}", roomCode);

        } catch (Exception e) {
            log.error("End game request failed: {}", e.getMessage());
            sendError(user.getUsername(), "Failed to retrieve end game results: " + resolveClientMessage(e, "Unexpected server error."));
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

    private String requireRoomCode(String roomCode) {
        if (roomCode == null || roomCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Room code is required.");
        }
        String normalized = roomCode.trim().toUpperCase();
        if (!normalized.matches("^[A-Z0-9]{5}$")) {
            throw new IllegalArgumentException("Room code must contain exactly 5 letters or numbers.");
        }
        return normalized;
    }

    private void requireQuestionId(Long questionId) {
        if (questionId == null) {
            throw new IllegalArgumentException("Question ID is required.");
        }
    }

    private void requireSelectedAnswerIndex(Integer selectedAnswerIndex) {
        if (selectedAnswerIndex == null) {
            throw new IllegalArgumentException("Selected answer index is required.");
        }
    }

    private String resolveClientMessage(Exception exception, String fallback) {
        if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
            String message = exception.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return fallback;
    }
}

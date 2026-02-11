package ro.mateistanescu.matquizspringbootbackend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import ro.mateistanescu.matquizspringbootbackend.dtos.AnswerProgressDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.QuestionDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.ResultsDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.UserSummaryDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.AnswerSubmissionRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.EndGameEarlyRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.GenerateQuizRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.JoinRoomRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.LeaveRoomRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.QuestionRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.ResultsRequest;
import ro.mateistanescu.matquizspringbootbackend.dtos.socket.StartGameRequest;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.Difficulty;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.enums.Role;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;
import ro.mateistanescu.matquizspringbootbackend.service.GameService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Validates websocket controller orchestration: request normalization, service
 * delegation, user-scoped queue messaging, room-topic broadcasts, and uniform
 * error propagation paths for each socket command.
 */
@ExtendWith(MockitoExtension.class)
class GameSocketControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private GameService gameService;

    @Mock
    private GameMapper gameMapper;

    @InjectMocks
    private GameSocketController gameSocketController;

    @Test
    @DisplayName("createRoom sends created room to host queue")
    void createRoomSendsCreatedRoom() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        SimpMessageHeaderAccessor headerAccessor = header("sess-1");
        GameRoom room = buildRoom("ABCDE", GameStatus.WAITING);
        GameRoomDto roomDto = buildRoomDto("ABCDE");

        when(gameService.createRoom(host, "sess-1")).thenReturn(room);
        when(gameMapper.toDto(room)).thenReturn(roomDto);

        gameSocketController.createRoom(principal, headerAccessor);

        verify(messagingTemplate).convertAndSendToUser("host", "/queue/created", roomDto);
    }

    @Test
    @DisplayName("createRoom sends error queue message when service fails")
    void createRoomSendsErrorWhenServiceFails() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        SimpMessageHeaderAccessor headerAccessor = header("sess-1");

        when(gameService.createRoom(host, "sess-1")).thenThrow(new IllegalStateException("boom"));

        gameSocketController.createRoom(principal, headerAccessor);

        verify(messagingTemplate).convertAndSendToUser("host", "/queue/errors", "Failed to create room: boom");
    }

    @Test
    @DisplayName("createRoom throws when principal is not authenticated token")
    void createRoomThrowsForInvalidPrincipalType() {
        Principal invalidPrincipal = () -> "host";

        assertThatThrownBy(() -> gameSocketController.createRoom(invalidPrincipal, header("sess-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User not authenticated");
    }

    @Test
    @DisplayName("joinRoom normalizes code and broadcasts room updates")
    void joinRoomSendsJoinedAndBroadcastsRoom() {
        User player = buildUser("alice");
        Principal principal = authenticatedPrincipal(player);
        SimpMessageHeaderAccessor headerAccessor = header("sess-2");
        JoinRoomRequest request = new JoinRoomRequest();
        request.setRoomCode("  abC12 ");

        GameRoom room = buildRoom("ABC12", GameStatus.WAITING);
        GameRoomDto roomDto = buildRoomDto("ABC12");

        when(gameService.joinRoom(player, "ABC12", "sess-2")).thenReturn(room);
        when(gameMapper.toDto(room)).thenReturn(roomDto);

        gameSocketController.joinRoom(request, principal, headerAccessor);

        verify(messagingTemplate).convertAndSendToUser("alice", "/queue/joined", roomDto);
        verify(messagingTemplate).convertAndSend("/topic/room/ABC12", roomDto);
    }

    @Test
    @DisplayName("joinRoom sends error queue message on failure")
    void joinRoomSendsErrorWhenServiceFails() {
        User player = buildUser("alice");
        Principal principal = authenticatedPrincipal(player);
        JoinRoomRequest request = new JoinRoomRequest();
        request.setRoomCode("ROOM1");

        when(gameService.joinRoom(player, "ROOM1", "sess-9"))
                .thenThrow(new IllegalArgumentException("Room ROOM1 not found"));

        gameSocketController.joinRoom(request, principal, header("sess-9"));

        verify(messagingTemplate).convertAndSendToUser("alice", "/queue/errors", "Room ROOM1 not found");
    }

    @Test
    @DisplayName("joinRoom sends error when roomCode is null")
    void joinRoomSendsErrorWhenRoomCodeIsNull() {
        User player = buildUser("alice");
        Principal principal = authenticatedPrincipal(player);
        JoinRoomRequest request = new JoinRoomRequest();
        request.setRoomCode(null);

        gameSocketController.joinRoom(request, principal, header("sess-10"));

        verify(gameService, never()).joinRoom(eq(player), any(), any());
        verify(messagingTemplate).convertAndSendToUser("alice", "/queue/errors", "Room code is required.");
    }

    @Test
    @DisplayName("joinRoom rejects blank roomCode")
    void joinRoomRejectsBlankRoomCode() {
        User player = buildUser("alice");
        Principal principal = authenticatedPrincipal(player);
        JoinRoomRequest request = new JoinRoomRequest();
        request.setRoomCode("   ");

        gameSocketController.joinRoom(request, principal, header("sess-11"));

        verify(gameService, never()).joinRoom(eq(player), any(), any());
        verify(messagingTemplate).convertAndSendToUser("alice", "/queue/errors", "Room code is required.");
    }

    @Test
    @DisplayName("reconnect ignores unauthenticated principals")
    void reconnectIgnoresNullPrincipal() {
        gameSocketController.reconnect(null, header("sess-3"));

        verifyNoInteractions(gameService, gameMapper, messagingTemplate);
    }

    @Test
    @DisplayName("reconnect sends user and room updates when room exists")
    void reconnectSendsQueueAndTopicMessages() {
        User player = buildUser("bob");
        Principal principal = authenticatedPrincipal(player);
        GameRoom room = buildRoom("ROOM9", GameStatus.PLAYING);
        GameRoomDto roomDto = buildRoomDto("ROOM9");

        when(gameService.handleReconnect("bob", "sess-3")).thenReturn(room);
        when(gameMapper.toDto(room)).thenReturn(roomDto);

        gameSocketController.reconnect(principal, header("sess-3"));

        verify(messagingTemplate).convertAndSendToUser("bob", "/queue/reconnected", roomDto);
        verify(messagingTemplate).convertAndSend("/topic/room/ROOM9", roomDto);
    }

    @Test
    @DisplayName("reconnect does not message when no active room exists")
    void reconnectSkipsMessagingWhenNoRoom() {
        User player = buildUser("bob");
        Principal principal = authenticatedPrincipal(player);

        when(gameService.handleReconnect("bob", "sess-3")).thenReturn(null);

        gameSocketController.reconnect(principal, header("sess-3"));

        verify(gameService).handleReconnect("bob", "sess-3");
        verifyNoInteractions(gameMapper);
        verify(messagingTemplate, never()).convertAndSendToUser(eq("bob"), eq("/queue/reconnected"), any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("reconnect sends prefixed error message on exception")
    void reconnectSendsPrefixedErrorMessageOnFailure() {
        User player = buildUser("bob");
        Principal principal = authenticatedPrincipal(player);
        when(gameService.handleReconnect("bob", "sess-4")).thenThrow(new IllegalStateException("broken"));

        gameSocketController.reconnect(principal, header("sess-4"));

        verify(messagingTemplate).convertAndSendToUser("bob", "/queue/errors", "Reconnect failed: broken");
    }

    @Test
    @DisplayName("leaveRoom ignores unauthenticated principals")
    void leaveRoomIgnoresNullPrincipal() {
        LeaveRoomRequest request = new LeaveRoomRequest();
        request.setRoomCode("ROOMA");

        gameSocketController.leaveRoom(request, null);

        verifyNoInteractions(gameService, gameMapper, messagingTemplate);
    }

    @Test
    @DisplayName("leaveRoom broadcasts room when service returns updated room")
    void leaveRoomBroadcastsUpdatedRoom() {
        User user = buildUser("claire");
        Principal principal = authenticatedPrincipal(user);
        LeaveRoomRequest request = new LeaveRoomRequest();
        request.setRoomCode("  rooma ");

        GameRoom room = buildRoom("ROOMA", GameStatus.WAITING);
        GameRoomDto roomDto = buildRoomDto("ROOMA");

        when(gameService.leaveRoom(user, "ROOMA")).thenReturn(room);
        when(gameMapper.toDto(room)).thenReturn(roomDto);

        gameSocketController.leaveRoom(request, principal);

        verify(messagingTemplate).convertAndSend("/topic/room/ROOMA", roomDto);
        verify(messagingTemplate).convertAndSendToUser("claire", "/queue/left", "ROOMA");
    }

    @Test
    @DisplayName("leaveRoom still acknowledges leaver when room was deleted")
    void leaveRoomStillAcknowledgesUserWhenRoomIsDeleted() {
        User user = buildUser("claire");
        Principal principal = authenticatedPrincipal(user);
        LeaveRoomRequest request = new LeaveRoomRequest();
        request.setRoomCode("rooma");

        when(gameService.leaveRoom(user, "ROOMA")).thenReturn(null);

        gameSocketController.leaveRoom(request, principal);

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/room/ROOMA"), any(Object.class));
        verify(messagingTemplate).convertAndSendToUser("claire", "/queue/left", "ROOMA");
    }

    @Test
    @DisplayName("leaveRoom sends error when roomCode is null")
    void leaveRoomSendsErrorWhenRoomCodeIsNull() {
        User user = buildUser("claire");
        Principal principal = authenticatedPrincipal(user);
        LeaveRoomRequest request = new LeaveRoomRequest();
        request.setRoomCode(null);

        gameSocketController.leaveRoom(request, principal);

        verify(gameService, never()).leaveRoom(eq(user), any());
        verify(messagingTemplate).convertAndSendToUser(
                "claire",
                "/queue/errors",
                "Leave room failed: Room code is required."
        );
    }

    @Test
    @DisplayName("leaveRoom rejects blank roomCode")
    void leaveRoomRejectsBlankRoomCode() {
        User user = buildUser("claire");
        Principal principal = authenticatedPrincipal(user);
        LeaveRoomRequest request = new LeaveRoomRequest();
        request.setRoomCode("   ");

        gameSocketController.leaveRoom(request, principal);

        verify(gameService, never()).leaveRoom(eq(user), any());
        verify(messagingTemplate).convertAndSendToUser(
                "claire",
                "/queue/errors",
                "Leave room failed: Room code is required."
        );
    }

    @Test
    @DisplayName("endGame ignores unauthenticated principals")
    void endGameIgnoresNullPrincipal() {
        EndGameEarlyRequest request = new EndGameEarlyRequest();
        request.setRoomCode("ROOMX");

        gameSocketController.endGame(request, null);

        verifyNoInteractions(gameService, messagingTemplate);
    }

    @Test
    @DisplayName("endGame broadcasts event then delegates to service")
    void endGameBroadcastsAndDelegates() {
        User user = buildUser("host");
        Principal principal = authenticatedPrincipal(user);
        EndGameEarlyRequest request = new EndGameEarlyRequest();
        request.setRoomCode("ROOMX");

        gameSocketController.endGame(request, principal);

        verify(messagingTemplate).convertAndSend("/topic/room/ROOMX", "END_GAME_EARLY");
        verify(gameService).endGameEarly(user, request);
    }

    @Test
    @DisplayName("endGame sends prefixed error when service throws")
    void endGameSendsPrefixedErrorOnFailure() {
        User user = buildUser("host");
        Principal principal = authenticatedPrincipal(user);
        EndGameEarlyRequest request = new EndGameEarlyRequest();
        request.setRoomCode("ROOMX");

        doThrow(new IllegalStateException("cannot end")).when(gameService).endGameEarly(user, request);

        gameSocketController.endGame(request, principal);

        verify(messagingTemplate).convertAndSendToUser("host", "/queue/errors", "End game failed: cannot end");
    }

    @Test
    @DisplayName("generateQuiz ignores unauthenticated principals")
    void generateQuizIgnoresNullPrincipal() {
        GenerateQuizRequest request = GenerateQuizRequest.builder()
                .roomCode("ROOMY")
                .topic("Math")
                .difficulty(Difficulty.EASY)
                .build();

        gameSocketController.generateQuiz(request, null);

        verifyNoInteractions(gameService, gameMapper, messagingTemplate);
    }

    @Test
    @DisplayName("generateQuiz broadcasts room when generation starts")
    void generateQuizBroadcastsRoomOnSuccess() {
        User user = buildUser("host");
        Principal principal = authenticatedPrincipal(user);
        GenerateQuizRequest request = GenerateQuizRequest.builder()
                .roomCode("ROOMY")
                .topic("Math")
                .difficulty(Difficulty.ADVANCED)
                .build();
        GameRoom room = buildRoom("ROOMY", GameStatus.GENERATING);
        GameRoomDto roomDto = buildRoomDto("ROOMY");

        when(gameService.requestQuizGeneration(user, request)).thenReturn(room);
        when(gameMapper.toDto(room)).thenReturn(roomDto);

        gameSocketController.generateQuiz(request, principal);

        verify(messagingTemplate).convertAndSend("/topic/room/ROOMY", roomDto);
    }

    @Test
    @DisplayName("generateQuiz emits no room update when service returns null")
    void generateQuizSkipsBroadcastWhenServiceReturnsNull() {
        User user = buildUser("host");
        Principal principal = authenticatedPrincipal(user);
        GenerateQuizRequest request = GenerateQuizRequest.builder()
                .roomCode("ROOMY")
                .topic("Math")
                .difficulty(Difficulty.EASY)
                .build();

        when(gameService.requestQuizGeneration(user, request)).thenReturn(null);

        gameSocketController.generateQuiz(request, principal);

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/room/ROOMY"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("host"), eq("/queue/errors"), any());
    }

    @Test
    @DisplayName("generateQuiz sends prefixed error when generation fails")
    void generateQuizSendsPrefixedErrorOnFailure() {
        User user = buildUser("host");
        Principal principal = authenticatedPrincipal(user);
        GenerateQuizRequest request = GenerateQuizRequest.builder()
                .roomCode("ROOMY")
                .topic("Math")
                .difficulty(Difficulty.EASY)
                .build();

        when(gameService.requestQuizGeneration(user, request)).thenThrow(new IllegalArgumentException("invalid topic"));

        gameSocketController.generateQuiz(request, principal);

        verify(messagingTemplate).convertAndSendToUser("host", "/queue/errors", "Generation failed: invalid topic");
    }

    @Test
    @DisplayName("startQuiz broadcasts updated room on successful game start")
    void startQuizBroadcastsRoomOnSuccess() {
        User user = buildUser("host");
        Principal principal = authenticatedPrincipal(user);
        StartGameRequest request = new StartGameRequest();
        request.setRoomCode("ROOM1");
        GameRoom room = buildRoom("ROOM1", GameStatus.PLAYING);
        GameRoomDto roomDto = buildRoomDto("ROOM1");

        when(gameService.startGame(user, request)).thenReturn(room);
        when(gameMapper.toDto(room)).thenReturn(roomDto);

        gameSocketController.startQuiz(request, principal);

        verify(messagingTemplate).convertAndSend("/topic/room/ROOM1", roomDto);
    }

    @Test
    @DisplayName("startQuiz sends no room message when service returns null")
    void startQuizSkipsBroadcastWhenServiceReturnsNull() {
        User user = buildUser("host");
        Principal principal = authenticatedPrincipal(user);
        StartGameRequest request = new StartGameRequest();
        request.setRoomCode("ROOM1");

        when(gameService.startGame(user, request)).thenReturn(null);

        gameSocketController.startQuiz(request, principal);

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/room/ROOM1"), any(Object.class));
    }

    @Test
    @DisplayName("startQuiz sends prefixed error when start fails")
    void startQuizSendsPrefixedErrorOnFailure() {
        User user = buildUser("host");
        Principal principal = authenticatedPrincipal(user);
        StartGameRequest request = new StartGameRequest();
        request.setRoomCode("ROOM1");

        when(gameService.startGame(user, request)).thenThrow(new IllegalStateException("not ready"));

        gameSocketController.startQuiz(request, principal);

        verify(messagingTemplate).convertAndSendToUser("host", "/queue/errors", "Game start failed: not ready");
    }

    @Test
    @DisplayName("requestQuestion broadcasts question and final room when finished")
    void requestQuestionBroadcastsQuestionAndFinalRoom() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        QuestionRequest request = new QuestionRequest();
        request.setRoomCode(" room5 ");

        QuestionDto questionDto = QuestionDto.builder().questionId(10L).question_text("2+2?").build();
        GameRoom finishedRoom = buildRoom("ROOM5", GameStatus.FINISHED);
        GameRoomDto roomDto = buildRoomDto("ROOM5");

        when(gameService.fetchQuestion(host, request)).thenReturn(questionDto);
        when(gameService.fetchFullRoom("ROOM5")).thenReturn(finishedRoom);
        when(gameMapper.toDto(finishedRoom)).thenReturn(roomDto);

        gameSocketController.requestQuestion(request, principal);

        verify(messagingTemplate).convertAndSend("/topic/room/ROOM5", questionDto);
        verify(messagingTemplate).convertAndSend("/topic/room/ROOM5", roomDto);
    }

    @Test
    @DisplayName("requestQuestion broadcasts only question when game is still running")
    void requestQuestionBroadcastsOnlyQuestionWhenNotFinished() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        QuestionRequest request = new QuestionRequest();
        request.setRoomCode("room5");

        QuestionDto questionDto = QuestionDto.builder().questionId(10L).question_text("2+2?").build();
        GameRoom runningRoom = buildRoom("ROOM5", GameStatus.PLAYING);

        when(gameService.fetchQuestion(host, request)).thenReturn(questionDto);
        when(gameService.fetchFullRoom("ROOM5")).thenReturn(runningRoom);

        gameSocketController.requestQuestion(request, principal);

        verify(messagingTemplate).convertAndSend("/topic/room/ROOM5", questionDto);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/room/ROOM5"), any(GameRoomDto.class));
    }

    @Test
    @DisplayName("requestQuestion sends prefixed error on service failure")
    void requestQuestionSendsPrefixedErrorOnFailure() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        QuestionRequest request = new QuestionRequest();
        request.setRoomCode("room5");

        when(gameService.fetchQuestion(host, request)).thenThrow(new IllegalStateException("no questions"));

        gameSocketController.requestQuestion(request, principal);

        verify(messagingTemplate).convertAndSendToUser("host", "/queue/errors", "Question request failed: no questions");
    }

    @Test
    @DisplayName("requestQuestion sends error when roomCode is null")
    void requestQuestionSendsErrorWhenRoomCodeIsNull() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        QuestionRequest request = new QuestionRequest();
        request.setRoomCode(null);
        gameSocketController.requestQuestion(request, principal);

        verify(gameService, never()).fetchQuestion(eq(host), any());
        verify(gameService, never()).fetchFullRoom(any());
        verify(messagingTemplate).convertAndSendToUser(
                "host",
                "/queue/errors",
                "Question request failed: Room code is required."
        );
    }

    @Test
    @DisplayName("requestQuestion rejects blank roomCode")
    void requestQuestionRejectsBlankRoomCode() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        QuestionRequest request = new QuestionRequest();
        request.setRoomCode("   ");

        gameSocketController.requestQuestion(request, principal);

        verify(gameService, never()).fetchQuestion(eq(host), any());
        verify(gameService, never()).fetchFullRoom(any());
        verify(messagingTemplate).convertAndSendToUser(
                "host",
                "/queue/errors",
                "Question request failed: Room code is required."
        );
    }

    @Test
    @DisplayName("submitAnswer acknowledges user and broadcasts progress")
    void submitAnswerAcknowledgesAndBroadcastsProgress() {
        User user = buildUser("player");
        Principal principal = authenticatedPrincipal(user);
        AnswerSubmissionRequest request = new AnswerSubmissionRequest();
        request.setRoomCode(" room7 ");
        request.setQuestionId(8L);
        request.setSelectedAnswerIndex(1);

        gameSocketController.submitAnswer(request, principal);

        verify(gameService).submitAnswer(eq(user), eq(request), any(LocalDateTime.class));
        verify(messagingTemplate).convertAndSendToUser("player", "/queue/submitAck", "OK");

        ArgumentCaptor<AnswerProgressDto> progressCaptor = ArgumentCaptor.forClass(AnswerProgressDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/ROOM7/progress"), progressCaptor.capture());
        assertThat(progressCaptor.getValue().getNickname()).isEqualTo("player");
        assertThat(progressCaptor.getValue().isAnswered()).isTrue();
    }

    @Test
    @DisplayName("submitAnswer sends prefixed error on submission failure")
    void submitAnswerSendsPrefixedErrorOnFailure() {
        User user = buildUser("player");
        Principal principal = authenticatedPrincipal(user);
        AnswerSubmissionRequest request = new AnswerSubmissionRequest();
        request.setRoomCode("room7");
        request.setQuestionId(8L);
        request.setSelectedAnswerIndex(1);

        doThrow(new IllegalArgumentException("late answer"))
                .when(gameService).submitAnswer(eq(user), eq(request), any(LocalDateTime.class));

        gameSocketController.submitAnswer(request, principal);

        verify(messagingTemplate).convertAndSendToUser("player", "/queue/errors", "Answer submission failed: late answer");
    }

    @Test
    @DisplayName("submitAnswer sends error when roomCode is null")
    void submitAnswerSendsErrorWhenRoomCodeIsNull() {
        User user = buildUser("player");
        Principal principal = authenticatedPrincipal(user);
        AnswerSubmissionRequest request = new AnswerSubmissionRequest();
        request.setRoomCode(null);
        request.setQuestionId(8L);
        request.setSelectedAnswerIndex(1);

        gameSocketController.submitAnswer(request, principal);

        verify(gameService, never()).submitAnswer(eq(user), any(), any());
        verify(messagingTemplate, never()).convertAndSendToUser("player", "/queue/submitAck", "OK");
        verify(messagingTemplate).convertAndSendToUser(
                "player",
                "/queue/errors",
                "Answer submission failed: Room code is required."
        );
    }

    @Test
    @DisplayName("submitAnswer rejects blank roomCode")
    void submitAnswerRejectsBlankRoomCode() {
        User user = buildUser("player");
        Principal principal = authenticatedPrincipal(user);
        AnswerSubmissionRequest request = new AnswerSubmissionRequest();
        request.setRoomCode("   ");
        request.setQuestionId(8L);
        request.setSelectedAnswerIndex(1);

        gameSocketController.submitAnswer(request, principal);

        verify(gameService, never()).submitAnswer(eq(user), any(), any());
        verify(messagingTemplate, never()).convertAndSendToUser("player", "/queue/submitAck", "OK");
        verify(messagingTemplate).convertAndSendToUser(
                "player",
                "/queue/errors",
                "Answer submission failed: Room code is required."
        );
    }

    @Test
    @DisplayName("endResults broadcasts normalized-room results")
    void endResultsBroadcastsNormalizedRoomResults() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        ResultsRequest request = new ResultsRequest();
        request.setRoomCode(" room8 ");
        ResultsDto resultsDto = ResultsDto.builder().players(List.of()).build();

        when(gameService.fetchRoomResults(host, request)).thenReturn(resultsDto);

        gameSocketController.endResults(request, principal);

        verify(messagingTemplate).convertAndSend("/topic/room/ROOM8", resultsDto);
    }

    @Test
    @DisplayName("endResults sends prefixed error on failure")
    void endResultsSendsPrefixedErrorOnFailure() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        ResultsRequest request = new ResultsRequest();
        request.setRoomCode("room8");

        when(gameService.fetchRoomResults(host, request)).thenThrow(new IllegalStateException("forbidden"));

        gameSocketController.endResults(request, principal);

        verify(messagingTemplate).convertAndSendToUser("host", "/queue/errors", "Failed to retrieve end game results: forbidden");
    }

    @Test
    @DisplayName("endResults sends error when roomCode is null")
    void endResultsSendsErrorWhenRoomCodeIsNull() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        ResultsRequest request = new ResultsRequest();
        request.setRoomCode(null);
        gameSocketController.endResults(request, principal);

        verify(gameService, never()).fetchRoomResults(eq(host), any());
        verify(messagingTemplate).convertAndSendToUser(
                "host",
                "/queue/errors",
                "Failed to retrieve end game results: Room code is required."
        );
    }

    @Test
    @DisplayName("endResults rejects blank roomCode")
    void endResultsRejectsBlankRoomCode() {
        User host = buildUser("host");
        Principal principal = authenticatedPrincipal(host);
        ResultsRequest request = new ResultsRequest();
        request.setRoomCode("   ");

        gameSocketController.endResults(request, principal);

        verify(gameService, never()).fetchRoomResults(eq(host), any());
        verify(messagingTemplate).convertAndSendToUser(
                "host",
                "/queue/errors",
                "Failed to retrieve end game results: Room code is required."
        );
    }

    @Test
    @DisplayName("endResults ignores unauthenticated principals")
    void endResultsIgnoresNullPrincipal() {
        ResultsRequest request = new ResultsRequest();
        request.setRoomCode("ROOM8");

        gameSocketController.endResults(request, null);

        verifyNoInteractions(gameService, messagingTemplate);
    }

    private SimpMessageHeaderAccessor header(String sessionId) {
        SimpMessageHeaderAccessor accessor = org.mockito.Mockito.mock(SimpMessageHeaderAccessor.class);
        org.mockito.Mockito.lenient().when(accessor.getSessionId()).thenReturn(sessionId);
        return accessor;
    }

    private Principal authenticatedPrincipal(User user) {
        return new UsernamePasswordAuthenticationToken(user, "n/a", List.of());
    }

    private User buildUser(String username) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setRole(Role.ROLE_USER);
        user.setPasswordHash("encoded");
        return user;
    }

    private GameRoom buildRoom(String roomCode, GameStatus status) {
        GameRoom room = new GameRoom();
        room.setRoomCode(roomCode);
        room.setStatus(status);
        room.setHost(buildUser("host"));
        room.setPlayers(List.of());
        return room;
    }

    private GameRoomDto buildRoomDto(String roomCode) {
        return GameRoomDto.builder()
                .roomCode(roomCode)
                .status(GameStatus.WAITING)
                .topic("Math")
                .difficulty(Difficulty.EASY)
                .host(UserSummaryDto.builder().username("host").build())
                .players(List.of())
                .build();
    }
}

package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mateistanescu.matquizspringbootbackend.dtos.GameRoomDto;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.mapper.GameMapper;
import ro.mateistanescu.matquizspringbootbackend.repository.GameRoomRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizTimeoutService {
    private final GameRoomRepository roomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameMapper gameMapper;

    @Transactional
    public void quizTimeoutCheck(String roomCode) {
        GameRoom updatedRoom = roomRepository.findByRoomCode(roomCode).orElseThrow();

        if (updatedRoom.getStatus() != GameStatus.GENERATING) {
            log.info("CHECK TIMEOUT: Quiz generated succesfully for room{} or error message broadcasted", roomCode);
            return;
        }

        log.info("CHECK TIMEOUT: Quiz generation timed out for room {}", roomCode);

        updatedRoom.setStatus(GameStatus.WAITING);
        roomRepository.save(updatedRoom);

        User host = updatedRoom.getHost();

        GameRoomDto roomDto = gameMapper.toDto(updatedRoom);

        messagingTemplate.convertAndSend(
                "/topic/room/" + roomCode,
                roomDto
        );

        messagingTemplate.convertAndSendToUser(
                host.getUsername(),
                "/queue/timeout",
                roomCode
        );
    }
}

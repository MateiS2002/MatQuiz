package ro.mateistanescu.matquizspringbootbackend.schedulingtasks;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.mateistanescu.matquizspringbootbackend.dtos.GamePulseDto;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.repository.GameRoomRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;


@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledTasks {

    private final GameRoomRepository gameRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;


    /**
     * Sanity check cleanup on startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        log.info("---------------------------Application started. Running initial sanity check for expired rooms..." +
                "---------------------------");
        performCleanup();
        log.info("---------------------------Sanity check completed.---------------------------");
    }


    /**
     * Cleans up WAITING rooms that are older than 24 hours.
     * Runs once every hour (3600000 ms).
     */
    @Scheduled(initialDelay = 60000, fixedRate = 3600000)
    @Transactional
    public void deleteExpiredRooms() {
        performCleanup();
    }

    private void performCleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        log.info("Starting cleanup of expired waiting rooms (older than {})...", cutoff);

        try {
            gameRoomRepository.deleteByStatusAndCreatedAtBefore(GameStatus.WAITING, cutoff);
            log.info("Cleanup completed successfully.");
        } catch (Exception e) {
            log.error("Failed to delete expired rooms: {}", e.getMessage());
        }
    }

    /**
     * Sends a lightweight heartbeat every 2 seconds to keep UI timers synced.
     */
    @Scheduled(fixedRate = 2000)
    public void broadcastGamePulses() {
        long startTime = System.currentTimeMillis();

        List<GameRoomRepository.GamePulseProjection> activeGames = gameRoomRepository.findAllActiveGames();

        if (activeGames.isEmpty()) return;

        for (GameRoomRepository.GamePulseProjection pulse : activeGames) {
            long remaining = 0;

            if (pulse.getQuestionStartedAt() != null) {
                // Calculate time left vs the 30-second limit
                long elapsed = Duration.between(pulse.getQuestionStartedAt(), LocalDateTime.now()).toMillis();
                remaining = Math.max(0, 30000 - elapsed);
            }

            GamePulseDto pulseDto = new GamePulseDto(
                    pulse.getRoomCode(),
                    pulse.getStatus().name(),
                    pulse.getCurrentQuestionIndex(),
                    remaining,
                    System.currentTimeMillis() // Absolute server time for client clock correction
            );

            // Broadcast to the specific room's pulse channel
            messagingTemplate.convertAndSend("/topic/room/" + pulse.getRoomCode() + "/pulse", pulseDto);
        }

        long endTime = System.currentTimeMillis();

//        log.info("Pulse broadcasted in {} ms", endTime - startTime); -- measured at 6-7 ms
    }
}

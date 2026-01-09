package ro.mateistanescu.matquizspringbootbackend.schedulingtasks;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.repository.GameRoomRepository;

import java.time.LocalDateTime;


@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledTasks {

    private final GameRoomRepository gameRoomRepository;


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
}

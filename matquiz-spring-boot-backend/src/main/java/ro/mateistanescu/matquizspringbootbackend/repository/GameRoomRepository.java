package ro.mateistanescu.matquizspringbootbackend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {
    Optional<GameRoom> findByRoomCode(String code);

    @EntityGraph(attributePaths = {"host", "players", "players.user"})
    Optional<GameRoom> findWithDetailsByRoomCode(String roomCode);

    @Modifying
    @Query("DELETE FROM GameRoom gr WHERE gr.status = :status AND gr.createdAt < :expirationTime")
    void deleteByStatusAndCreatedAtBefore(GameStatus status, LocalDateTime expirationTime);
}

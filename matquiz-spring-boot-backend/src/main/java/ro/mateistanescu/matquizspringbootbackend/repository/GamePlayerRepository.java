package ro.mateistanescu.matquizspringbootbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ro.mateistanescu.matquizspringbootbackend.entity.GamePlayer;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {
    Optional<GamePlayer> findByUserAndGameRoom(User user, GameRoom gameRoom);

    Optional<GamePlayer> findBySocketSessionId(String socketSessionId);

    @Query("SELECT gp FROM GamePlayer gp " +
            "JOIN gp.gameRoom gr " +
            "WHERE gp.user.username = :username " +
            "AND gr.status <> :status " +
            "ORDER BY gp.joinedAt DESC")
    List<GamePlayer> findAllActiveGamesForUser(String username, GameStatus status);

}

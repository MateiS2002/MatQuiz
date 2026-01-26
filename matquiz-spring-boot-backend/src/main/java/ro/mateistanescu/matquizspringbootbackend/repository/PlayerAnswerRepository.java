package ro.mateistanescu.matquizspringbootbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ro.mateistanescu.matquizspringbootbackend.entity.GamePlayer;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.PlayerAnswer;
import ro.mateistanescu.matquizspringbootbackend.entity.Question;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerAnswerRepository extends JpaRepository<PlayerAnswer, Long> {
    Optional<PlayerAnswer> findByGamePlayerAndQuestion(GamePlayer player, Question question);

    @Query("SELECT pa FROM PlayerAnswer pa JOIN pa.gamePlayer gp WHERE pa.question = :question AND gp.gameRoom = :room")
    List<PlayerAnswer> findByQuestionAndGameRoom(@Param("question") Question question, @Param("room") GameRoom room);
}

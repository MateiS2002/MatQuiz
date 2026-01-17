package ro.mateistanescu.matquizspringbootbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.mateistanescu.matquizspringbootbackend.entity.GamePlayer;
import ro.mateistanescu.matquizspringbootbackend.entity.PlayerAnswer;
import ro.mateistanescu.matquizspringbootbackend.entity.Question;

import java.util.Optional;

@Repository
public interface PlayerAnswerRepository extends JpaRepository<PlayerAnswer, Long> {
    Optional<PlayerAnswer> findByGamePlayerAndQuestion(GamePlayer player, Question question);
}

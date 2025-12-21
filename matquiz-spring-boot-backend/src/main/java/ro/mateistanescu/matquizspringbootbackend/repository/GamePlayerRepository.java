package ro.mateistanescu.matquizspringbootbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.mateistanescu.matquizspringbootbackend.entity.GamePlayer;

@Repository
public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {
}

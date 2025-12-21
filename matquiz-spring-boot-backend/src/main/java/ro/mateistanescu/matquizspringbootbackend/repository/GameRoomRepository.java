package ro.mateistanescu.matquizspringbootbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;

@Repository
public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {
}

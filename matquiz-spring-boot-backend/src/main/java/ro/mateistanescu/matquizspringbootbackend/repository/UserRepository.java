package ro.mateistanescu.matquizspringbootbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.mateistanescu.matquizspringbootbackend.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}

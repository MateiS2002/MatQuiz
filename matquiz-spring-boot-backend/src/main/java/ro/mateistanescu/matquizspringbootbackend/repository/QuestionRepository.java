package ro.mateistanescu.matquizspringbootbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.mateistanescu.matquizspringbootbackend.entity.Question;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
}

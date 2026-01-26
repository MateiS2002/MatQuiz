package ro.mateistanescu.matquizspringbootbackend.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.entity.Question;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;
import ro.mateistanescu.matquizspringbootbackend.repository.GameRoomRepository;
import ro.mateistanescu.matquizspringbootbackend.repository.QuestionRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockQuestionGenerator implements QuestionGeneratorService{
    private final QuestionRepository questionRepository;
    private final GameRoomRepository gameRoomRepository;


    //In the real service when this method is called actually, a message will be sent in the RabbitMQ queue
    @Override
    public void generateQuestions(GameRoom room) {
        log.info("MOCK AI: Generating questions for topic '{}'", room.getTopic());

        //simulate the status of the room
        this.updateStatusOfRoom(room, GameStatus.GENERATING);

        List<Question> questions = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            Question q = Question.builder()
                    .gameRoom(room)
                    .questionText("Mock Question " + i + ": What is 2 + " + i + "?")
                    .answers(List.of("3", "4", "5", "6"))
                    .correctIndex(i-1) // 0-based index
                    .orderIndex(i)
                    .build();
            questions.add(q);
        }

        questionRepository.saveAll(questions);
        room.setQuestions(questions);

        log.info("MOCK AI: Saved {} questions to DB for room {}", questions.size(), room.getRoomCode());

        //simulate the status of the room
        this.updateStatusOfRoom(room, GameStatus.READY);
    }

    @Override
    public void updateStatusOfRoom(GameRoom room, GameStatus status){
        room.setStatus(status);
        gameRoomRepository.save(room);
    }
}

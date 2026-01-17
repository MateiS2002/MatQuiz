package ro.mateistanescu.matquizspringbootbackend.service;

import ro.mateistanescu.matquizspringbootbackend.entity.GameRoom;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;

public interface QuestionGeneratorService {
    void generateQuestions(GameRoom room);
    void updateStatusOfRoom(GameRoom room, GameStatus status);
}

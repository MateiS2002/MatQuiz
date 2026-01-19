package ro.mateistanescu.matquizspringbootbackend.dtos.socket;

import lombok.Data;

@Data
public class EndGameEarlyRequest {
    private String roomCode;
}

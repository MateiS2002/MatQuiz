package ro.mateistanescu.matquizspringbootbackend.dtos.socket;

import lombok.Data;

@Data
public class StartGameRequest {
    private String roomCode;
}

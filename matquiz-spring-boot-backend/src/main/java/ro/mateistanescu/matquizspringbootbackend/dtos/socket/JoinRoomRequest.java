package ro.mateistanescu.matquizspringbootbackend.dtos.socket;

import lombok.Data;

@Data
public class JoinRoomRequest {
    private String roomCode;
}

package ro.mateistanescu.matquizspringbootbackend.dtos.socket;

import lombok.Data;

@Data
public class LeaveRoomRequest {
    private String roomCode;
}

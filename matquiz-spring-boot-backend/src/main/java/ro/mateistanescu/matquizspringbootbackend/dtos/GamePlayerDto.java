package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GamePlayerDto {
    private String nickname;
    private Integer score;
    private Boolean isConnected;
    private String avatarUrl;
}

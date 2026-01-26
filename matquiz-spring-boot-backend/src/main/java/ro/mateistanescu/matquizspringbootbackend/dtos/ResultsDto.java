package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Builder;
import lombok.Data;
import ro.mateistanescu.matquizspringbootbackend.entity.GamePlayer;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ResultsDto {
    @Builder.Default
    private LocalDateTime endTime  = LocalDateTime.now();
    private List<GamePlayerDto> players;
}

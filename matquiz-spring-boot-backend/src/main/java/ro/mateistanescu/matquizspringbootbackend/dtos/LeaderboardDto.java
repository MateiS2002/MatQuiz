package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardDto {
    private Long rank;
    private String username;
    private Integer eloRating;
}

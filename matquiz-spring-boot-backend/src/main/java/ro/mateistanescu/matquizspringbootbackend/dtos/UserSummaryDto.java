package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Builder;
import lombok.Data;
import ro.mateistanescu.matquizspringbootbackend.enums.Role;

@Data
@Builder
public class UserSummaryDto {
    private Long id;
    private String username;
    private String email;
    private Role role;
    private Integer eloRating;
    private String avatarUrl;
}
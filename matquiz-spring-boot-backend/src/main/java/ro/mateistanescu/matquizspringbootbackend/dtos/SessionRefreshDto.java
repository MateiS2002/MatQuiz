package ro.mateistanescu.matquizspringbootbackend.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionRefreshDto {
    private String accessToken;
    private UserSummaryDto user;
}

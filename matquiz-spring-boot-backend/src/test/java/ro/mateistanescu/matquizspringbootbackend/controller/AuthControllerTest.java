package ro.mateistanescu.matquizspringbootbackend.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import ro.mateistanescu.matquizspringbootbackend.dtos.*;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.Role;
import ro.mateistanescu.matquizspringbootbackend.service.GameService;
import ro.mateistanescu.matquizspringbootbackend.service.UserService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises AuthController as a focused unit to cover endpoint behavior,
 * authorization fallbacks, session refresh responses, and validation-adjacent
 * service failures without relying on full Spring MVC security wiring.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private GameService gameService;

    @InjectMocks
    private AuthController authController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("login delegates to user service and returns token")
    void loginReturnsToken() {
        UserLoginDto request = new UserLoginDto();
        request.setUsername("john");
        request.setPassword("secret");

        when(userService.authenticate("john", "secret")).thenReturn("token-123");

        String token = authController.login(request);

        assertThat(token).isEqualTo("token-123");
    }

    @Test
    @DisplayName("protectedWhoami returns current principal and authorities")
    void protectedWhoamiReturnsPrincipalDetails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("john", "n/a", List.of())
        );

        String response = authController.protectedWhoami();

        assertThat(response).contains("You are: john");
        assertThat(response).contains("authorities []");
    }

    @Test
    @DisplayName("register creates user and returns created true")
    void registerReturnsCreated() {
        UserRegisterDto request = new UserRegisterDto();
        request.setUsername("john");
        request.setEmail("john@example.com");
        request.setPassword("Password123");

        ResponseEntity<Boolean> response = authController.register(request);

        verify(userService).addUser("john", "john@example.com", "Password123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    @DisplayName("getLeaderboard without body loads full leaderboard")
    void getLeaderboardReturnsFullLeaderboard() {
        List<LeaderboardDto> leaderboard = List.of(
                LeaderboardDto.builder().rank(1L).username("alice").eloRating(1300).build()
        );
        when(userService.getLeaderboard(null)).thenReturn(leaderboard);

        ResponseEntity<List<LeaderboardDto>> response = authController.getLeaderboard();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(leaderboard);
    }

    @Test
    @DisplayName("getLeaderboard with request loads player-centric leaderboard")
    void getLeaderboardByUsernameReturnsFilteredLeaderboard() {
        LeaderboardRequestDto request = LeaderboardRequestDto.builder().username("john").build();
        List<LeaderboardDto> leaderboard = List.of(
                LeaderboardDto.builder().rank(3L).username("john").eloRating(1210).build()
        );

        when(userService.getLeaderboard("john")).thenReturn(leaderboard);

        ResponseEntity<List<LeaderboardDto>> response = authController.getLeaderboard(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(leaderboard);
    }

    @Test
    @DisplayName("getActiveGame returns unauthorized when principal missing")
    void getActiveGameReturnsUnauthorizedForMissingPrincipal() {
        ResponseEntity<ActiveGameDto> response = authController.getActiveGame(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getActiveGame returns unauthorized when principal user missing")
    void getActiveGameReturnsUnauthorizedWhenUserNotFound() {
        Principal principal = () -> "john";
        when(userService.findByUsername("john")).thenReturn(null);

        ResponseEntity<ActiveGameDto> response = authController.getActiveGame(principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getActiveGame returns active flag from game service")
    void getActiveGameReturnsActiveStatus() {
        Principal principal = () -> "john";
        User user = buildUser("john", "john@example.com");
        when(userService.findByUsername("john")).thenReturn(user);
        when(gameService.hasActiveGame("john")).thenReturn(true);

        ResponseEntity<ActiveGameDto> response = authController.getActiveGame(principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("hasActiveGame", true);
    }

    @Test
    @DisplayName("setProfilePicture returns unauthorized for missing principal")
    void setProfilePictureReturnsUnauthorizedForMissingPrincipal() {
        SetProfilePictureRequestDto request = new SetProfilePictureRequestDto();
        request.setAvatarUrl("https://example.com/a.png");

        ResponseEntity<?> response = authController.setProfilePicture(null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("setProfilePicture returns unauthorized when user cannot be resolved")
    void setProfilePictureReturnsUnauthorizedWhenUserMissing() {
        Principal principal = () -> "john";
        SetProfilePictureRequestDto request = new SetProfilePictureRequestDto();
        request.setAvatarUrl("https://example.com/a.png");

        when(userService.findByUsername("john")).thenReturn(null);

        ResponseEntity<?> response = authController.setProfilePicture(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("setProfilePicture returns updated user summary on success")
    void setProfilePictureReturnsUpdatedUser() {
        Principal principal = () -> "john";
        User currentUser = buildUser("john", "john@example.com");
        User updatedUser = buildUser("john", "john@example.com");
        updatedUser.setAvatarUrl("https://example.com/new.png");

        SetProfilePictureRequestDto request = new SetProfilePictureRequestDto();
        request.setAvatarUrl("https://example.com/new.png");

        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.setProfilePicture(currentUser, "https://example.com/new.png")).thenReturn(updatedUser);

        ResponseEntity<?> response = authController.setProfilePicture(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserSummaryDto body = (UserSummaryDto) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getUsername()).isEqualTo("john");
        assertThat(body.getAvatarUrl()).isEqualTo("https://example.com/new.png");
    }

    @Test
    @DisplayName("setProfilePicture returns bad request on service validation error")
    void setProfilePictureReturnsBadRequestOnValidationFailure() {
        Principal principal = () -> "john";
        User currentUser = buildUser("john", "john@example.com");
        SetProfilePictureRequestDto request = new SetProfilePictureRequestDto();
        request.setAvatarUrl("invalid-avatar");

        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.setProfilePicture(currentUser, "invalid-avatar"))
                .thenThrow(new IllegalArgumentException("Invalid avatar url"));

        ResponseEntity<?> response = authController.setProfilePicture(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "Invalid avatar url"));
    }

    @Test
    @DisplayName("changeUsername returns unauthorized for missing principal")
    void changeUsernameReturnsUnauthorizedForMissingPrincipal() {
        ChangeUsernameRequestDto request = new ChangeUsernameRequestDto();
        request.setNewUsername("johnny");

        ResponseEntity<?> response = authController.changeUsername(null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("changeUsername returns refreshed session payload on success")
    void changeUsernameReturnsRefreshedSession() {
        Principal principal = () -> "john";
        User currentUser = buildUser("john", "john@example.com");
        User updatedUser = buildUser("johnny", "john@example.com");

        ChangeUsernameRequestDto request = new ChangeUsernameRequestDto();
        request.setNewUsername("johnny");

        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.changeUsername(currentUser, "johnny")).thenReturn(updatedUser);
        when(userService.issueSessionToken("johnny")).thenReturn("new-token");

        ResponseEntity<?> response = authController.changeUsername(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessionRefreshDto body = (SessionRefreshDto) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isEqualTo("new-token");
        assertThat(body.getUser().getUsername()).isEqualTo("johnny");
    }

    @Test
    @DisplayName("changeUsername returns bad request on invalid requested username")
    void changeUsernameReturnsBadRequestOnValidationFailure() {
        Principal principal = () -> "john";
        User currentUser = buildUser("john", "john@example.com");
        ChangeUsernameRequestDto request = new ChangeUsernameRequestDto();
        request.setNewUsername("taken");

        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.changeUsername(currentUser, "taken"))
                .thenThrow(new IllegalArgumentException("Username already exists"));

        ResponseEntity<?> response = authController.changeUsername(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "Username already exists"));
    }

    @Test
    @DisplayName("changeEmail returns refreshed session payload on success")
    void changeEmailReturnsRefreshedSession() {
        Principal principal = () -> "john";
        User currentUser = buildUser("john", "john@example.com");
        User updatedUser = buildUser("john", "johnny@example.com");
        ChangeEmailRequestDto request = new ChangeEmailRequestDto();
        request.setNewEmail("johnny@example.com");

        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.changeEmail(currentUser, "johnny@example.com")).thenReturn(updatedUser);
        when(userService.issueSessionToken("john")).thenReturn("email-token");

        ResponseEntity<?> response = authController.changeEmail(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessionRefreshDto body = (SessionRefreshDto) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isEqualTo("email-token");
        assertThat(body.getUser().getEmail()).isEqualTo("johnny@example.com");
    }

    @Test
    @DisplayName("changeEmail returns bad request on service validation failure")
    void changeEmailReturnsBadRequestOnValidationFailure() {
        Principal principal = () -> "john";
        User currentUser = buildUser("john", "john@example.com");
        ChangeEmailRequestDto request = new ChangeEmailRequestDto();
        request.setNewEmail("dup@example.com");

        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.changeEmail(currentUser, "dup@example.com"))
                .thenThrow(new IllegalArgumentException("Email already exists"));

        ResponseEntity<?> response = authController.changeEmail(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "Email already exists"));
    }

    @Test
    @DisplayName("changePassword returns refreshed session payload on success")
    void changePasswordReturnsRefreshedSession() {
        Principal principal = () -> "john";
        User currentUser = buildUser("john", "john@example.com");
        ChangePasswordRequestDto request = new ChangePasswordRequestDto();
        request.setCurrentPassword("old-secret");
        request.setNewPassword("new-secret-123");

        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.changePassword(currentUser, "old-secret", "new-secret-123")).thenReturn(currentUser);
        when(userService.issueSessionToken("john")).thenReturn("password-token");

        ResponseEntity<?> response = authController.changePassword(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessionRefreshDto body = (SessionRefreshDto) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isEqualTo("password-token");
        assertThat(body.getUser().getUsername()).isEqualTo("john");
    }

    @Test
    @DisplayName("changePassword returns bad request on invalid password change")
    void changePasswordReturnsBadRequestOnValidationFailure() {
        Principal principal = () -> "john";
        User currentUser = buildUser("john", "john@example.com");
        ChangePasswordRequestDto request = new ChangePasswordRequestDto();
        request.setCurrentPassword("old-secret");
        request.setNewPassword("weak");

        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.changePassword(currentUser, "old-secret", "weak"))
                .thenThrow(new IllegalArgumentException("Current password is invalid"));

        ResponseEntity<?> response = authController.changePassword(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "Current password is invalid"));
    }

    @Test
    @DisplayName("getSession returns unauthorized when username cannot be resolved")
    void getSessionReturnsUnauthorizedWhenUserMissing() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost", "n/a", List.of())
        );
        when(userService.findByUsername("ghost")).thenReturn(null);

        ResponseEntity<UserSummaryDto> response = authController.getSession();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getSession returns authenticated user summary when user exists")
    void getSessionReturnsUserSummary() {
        User user = buildUser("john", "john@example.com");
        user.setAvatarUrl("https://example.com/a.png");
        user.setEloRating(1240);
        user.setLastGamePoints(8);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("john", "n/a", List.of())
        );
        when(userService.findByUsername("john")).thenReturn(user);

        ResponseEntity<UserSummaryDto> response = authController.getSession();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUsername()).isEqualTo("john");
        assertThat(response.getBody().getEmail()).isEqualTo("john@example.com");
        assertThat(response.getBody().getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(response.getBody().getAvatarUrl()).isEqualTo("https://example.com/a.png");
        assertThat(response.getBody().getEloRating()).isEqualTo(1240);
        assertThat(response.getBody().getLastGamePoints()).isEqualTo(8);
    }

    private User buildUser(String username, String email) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(Role.ROLE_USER);
        user.setPasswordHash("encoded");
        user.setEloRating(1000);
        user.setLastGamePoints(0);
        user.setAvatarUrl(null);
        return user;
    }
}

package ro.mateistanescu.matquizspringbootbackend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ro.mateistanescu.matquizspringbootbackend.dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.service.GameService;
import ro.mateistanescu.matquizspringbootbackend.service.UserService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final UserService userService;
    private final GameService gameService;

    @PostMapping("/login")
    public String login(@RequestBody @Valid UserLoginDto dto) {
        return userService.authenticate(dto.getUsername(), dto.getPassword());
    }

    @GetMapping("/whoami")
    public String protectedWhoami() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return "You are: " + authentication.getName() + " with authorities " + authentication.getAuthorities();
    }

    @PostMapping("/register")
    public ResponseEntity<Boolean> register(@RequestBody @Valid UserRegisterDto dto) {
        userService.addUser(dto.getUsername(), dto.getEmail(), dto.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(true);
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardDto>> getLeaderboard() {
        List<LeaderboardDto> leaderboard = userService.getLeaderboard(null);
        log.info("--- REST Leaderboard: Returned {} entries from leaderboard", leaderboard.size());
        return ResponseEntity.ok(leaderboard);
    }

    @PostMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardDto>> getLeaderboard(@RequestBody @Valid LeaderboardRequestDto request) {
        List<LeaderboardDto> leaderboard = userService.getLeaderboard(request.getUsername());
        log.info("--- REST Leaderboard: Returned entry for user {}", request.getUsername());
        return ResponseEntity.ok(leaderboard);
    }

    @GetMapping("/active")
    public ResponseEntity<ActiveGameDto> getActiveGame(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean hasActiveGame = gameService.hasActiveGame(user.getUsername());

        if(hasActiveGame){
            log.info("User {} has an active game", user.getUsername());
        }
        return ResponseEntity.ok(new ActiveGameDto(hasActiveGame));
    }

    @PatchMapping("/setProfilePicture")
    public ResponseEntity<?> setProfilePicture(Principal principal, @RequestBody @Valid SetProfilePictureRequestDto request){
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User updatedUser = userService.setProfilePicture(user, request.getAvatarUrl());
            return ResponseEntity.ok(toUserSummary(updatedUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/changeUsername")
    public ResponseEntity<?> changeUsername(Principal principal, @RequestBody @Valid ChangeUsernameRequestDto request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User updatedUser = userService.changeUsername(user, request.getNewUsername());
            String accessToken = userService.issueSessionToken(updatedUser.getUsername());
            return ResponseEntity.ok(SessionRefreshDto.builder()
                    .accessToken(accessToken)
                    .user(toUserSummary(updatedUser))
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/changeEmail")
    public ResponseEntity<?> changeEmail(Principal principal, @RequestBody @Valid ChangeEmailRequestDto request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User updatedUser = userService.changeEmail(user, request.getNewEmail());
            String accessToken = userService.issueSessionToken(updatedUser.getUsername());
            return ResponseEntity.ok(SessionRefreshDto.builder()
                    .accessToken(accessToken)
                    .user(toUserSummary(updatedUser))
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/changePassword")
    public ResponseEntity<?> changePassword(Principal principal, @RequestBody @Valid ChangePasswordRequestDto request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User updatedUser = userService.changePassword(user, request.getCurrentPassword(), request.getNewPassword());
            String accessToken = userService.issueSessionToken(updatedUser.getUsername());
            return ResponseEntity.ok(SessionRefreshDto.builder()
                    .accessToken(accessToken)
                    .user(toUserSummary(updatedUser))
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }


    @GetMapping("/session")
    public ResponseEntity<UserSummaryDto> getSession() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        User user = userService.findByUsername(authentication.getName());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(toUserSummary(user));
    }

    private UserSummaryDto toUserSummary(User user) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .eloRating(user.getEloRating())
                .lastGamePoints(user.getLastGamePoints())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}

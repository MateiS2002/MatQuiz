package ro.mateistanescu.matquizspringbootbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ro.mateistanescu.matquizspringbootbackend.dtos.UserLoginDto;
import ro.mateistanescu.matquizspringbootbackend.dtos.UserRegisterDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ro.mateistanescu.matquizspringbootbackend.dtos.UserSummaryDto;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.service.UserService;

@RestController
@RequestMapping(value = "/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

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

    @GetMapping("/session")
    public ResponseEntity<UserSummaryDto> getSession() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        User user = userService.findByUsername(authentication.getName());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(UserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .eloRating(user.getEloRating())
                .avatarUrl(user.getAvatarUrl())
                .build());
    }
}
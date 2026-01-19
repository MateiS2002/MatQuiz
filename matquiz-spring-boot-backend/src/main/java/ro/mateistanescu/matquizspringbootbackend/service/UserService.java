package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ro.mateistanescu.matquizspringbootbackend.dtos.LeaderboardDto;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.Role;
import ro.mateistanescu.matquizspringbootbackend.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class UserService {
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * Registers a new user with a hashed password and a default role.
     */
    public void addUser(String username, String email, String password) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ROLE_USER) // Default role from our Enum
                .eloRating(1000)
                .totalGamesPlayed(0)
                .lastGamePoints(0)
                .build();

        userRepository.save(user);
    }

    /**
     * Authenticates credentials and returns a signed JWT.
     */
    public String authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .map(u -> jwtService.createToken(u.getUsername()))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
    }

    /**
     * Validates JWT and retrieves User object from database.
     */
    public User validateUser(String token) {
        String username = jwtService.validateToken(token);
        return userRepository.findByUsername(username)
                .orElse(null);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * Gets the top 100 users ordered by ELO rating descending.
     * If a username is provided, filters to show only that user's position.
     * Returns a list of LeaderboardDto objects with rank information.
     */
    public List<LeaderboardDto> getLeaderboard(String username) {
        List<User> topUsers = userRepository.findTop100ByOrderByEloRatingDesc();

        // If a username is provided, find that user's rank
        if (username != null && !username.isBlank()) {
            Optional<User> userOptional = userRepository.findByUsername(username);
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                // Create a list with just that user
                return List.of(LeaderboardDto.builder()
                        .rank((long) topUsers.indexOf(user) + 1)
                        .username(user.getUsername())
                        .eloRating(user.getEloRating())
                        .build());
            }
        }

        // Otherwise, return full leaderboard
        return IntStream.range(0, topUsers.size())
                .mapToObj(index -> {
                    User user = topUsers.get(index);
                    return LeaderboardDto.builder()
                            .rank((long) index + 1)
                            .username(user.getUsername())
                            .eloRating(user.getEloRating())
                            .build();
                })
                .collect(Collectors.toList());
    }
}

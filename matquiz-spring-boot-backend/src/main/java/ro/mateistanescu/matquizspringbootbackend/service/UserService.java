package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.Role;
import ro.mateistanescu.matquizspringbootbackend.repository.UserRepository;

import java.util.Optional;

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
     * Validates the JWT and retrieves the User object from the database.
     */
    public User validateUser(String token) {
        String username = jwtService.validateToken(token);
        return userRepository.findByUsername(username)
                .orElse(null);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }
}
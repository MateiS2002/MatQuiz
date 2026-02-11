package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mateistanescu.matquizspringbootbackend.configuration.UserAvatarProperties;
import ro.mateistanescu.matquizspringbootbackend.dtos.LeaderboardDto;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.Role;
import ro.mateistanescu.matquizspringbootbackend.exception.UserRegistrationConflictException;
import ro.mateistanescu.matquizspringbootbackend.repository.UserRepository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final String USERNAME_TAKEN_MESSAGE = "This username is already taken.";
    private static final String EMAIL_IN_USE_MESSAGE = "This email is already in use.";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$");
    private static final Pattern AVATAR_FILENAME_PATTERN = Pattern.compile("^(\\d+)\\.png$");

    private final UserAvatarProperties userAvatarProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * Registers a new user with a hashed password and a default role.
     */
    @Transactional
    public void addUser(String username, String email, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UserRegistrationConflictException(USERNAME_TAKEN_MESSAGE);
        }

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new UserRegistrationConflictException(EMAIL_IN_USE_MESSAGE);
        }

        User user = User.builder()
                .username(normalizedUsername)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .avatarUrl(generateRandomAvatarUrl())
                .role(Role.ROLE_USER) // Default role from our Enum
                .eloRating(1000)
                .totalGamesPlayed(0)
                .lastGamePoints(0)
                .build();

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw mapRegistrationConflict(ex);
        }
    }

    @Transactional
    public User setProfilePicture(User user, String avatarUrl) {
        String normalizedAvatarUrl = avatarUrl == null ? "" : avatarUrl.trim();

        if (!isValidAvatarUrl(normalizedAvatarUrl)) {
            throw new IllegalArgumentException("Invalid avatar URL.");
        }

        user.setAvatarUrl(normalizedAvatarUrl);
        return userRepository.save(user);
    }

    @Transactional
    public User changeUsername(User user, String newUsername) {
        String normalizedUsername = normalizeUsername(newUsername);

        if (user.getUsername().equalsIgnoreCase(normalizedUsername)) {
            throw new IllegalArgumentException("New username must be different from the current username.");
        }

        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, user.getId())) {
            throw new IllegalArgumentException(USERNAME_TAKEN_MESSAGE);
        }

        user.setUsername(normalizedUsername);
        return userRepository.save(user);
    }

    @Transactional
    public User changeEmail(User user, String newEmail) {
        String normalizedEmail = normalizeEmail(newEmail);

        if (user.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("New email must be different from the current email.");
        }

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, user.getId())) {
            throw new IllegalArgumentException(EMAIL_IN_USE_MESSAGE);
        }

        user.setEmail(normalizedEmail);
        return userRepository.save(user);
    }

    @Transactional
    public User changePassword(User user, String currentPassword, String newPassword) {
        String normalizedCurrentPassword = currentPassword == null ? "" : currentPassword;
        String normalizedNewPassword = newPassword == null ? "" : newPassword;

        if (!passwordEncoder.matches(normalizedCurrentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(normalizedNewPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password.");
        }

        if (!PASSWORD_PATTERN.matcher(normalizedNewPassword).matches()) {
            throw new IllegalArgumentException("New password must be 8-72 chars and include uppercase, lowercase, and a digit.");
        }

        user.setPasswordHash(passwordEncoder.encode(normalizedNewPassword));
        return userRepository.save(user);
    }

    public String issueSessionToken(String username) {
        return jwtService.createToken(username);
    }

    private String generateRandomAvatarUrl() {
        int avatarNumber = ThreadLocalRandom.current().nextInt(1, userAvatarProperties.getImageCount() + 1);
        return normalizedAvatarBaseUrl() + avatarNumber + ".png";
    }

    private boolean isValidAvatarUrl(String avatarUrl) {
        String baseUrl = normalizedAvatarBaseUrl();
        if (!avatarUrl.startsWith(baseUrl)) {
            return false;
        }

        String fileName = avatarUrl.substring(baseUrl.length());
        Matcher matcher = AVATAR_FILENAME_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return false;
        }

        try {
            int avatarNumber = Integer.parseInt(matcher.group(1));
            return avatarNumber >= 1 && avatarNumber <= userAvatarProperties.getImageCount();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String normalizedAvatarBaseUrl() {
        String baseUrl = userAvatarProperties.getBaseUrl().trim();
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
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
     * Validates JWT and retrieves a User object from the database.
     */
    public User validateUser(String token) {
        String username = jwtService.validateToken(token);
        return userRepository.findByUsername(username)
                .orElse(null);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    private String normalizeUsername(String username) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw new IllegalArgumentException("Username must be 3-30 chars and contain letters, digits, or underscores.");
        }
        return normalizedUsername;
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        return normalizedEmail;
    }

    private RuntimeException mapRegistrationConflict(DataIntegrityViolationException ex) {
        Throwable rootCause = ex.getMostSpecificCause();
        String message = rootCause == null ? ex.getMessage() : rootCause.getMessage();
        if (message == null) {
            return ex;
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("users_username_key") || normalizedMessage.contains("(username)")) {
            return new UserRegistrationConflictException(USERNAME_TAKEN_MESSAGE);
        }

        if (normalizedMessage.contains("users_email_key") || normalizedMessage.contains("(email)")) {
            return new UserRegistrationConflictException(EMAIL_IN_USE_MESSAGE);
        }

        return ex;
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

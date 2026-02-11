package ro.mateistanescu.matquizspringbootbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.mateistanescu.matquizspringbootbackend.configuration.UserAvatarProperties;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.exception.UserRegistrationConflictException;
import ro.mateistanescu.matquizspringbootbackend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        UserAvatarProperties userAvatarProperties = new UserAvatarProperties();
        userAvatarProperties.setBaseUrl("https://cdn.example.com/avatars/");
        userAvatarProperties.setImageCount(12);

        userService = new UserService(userAvatarProperties, passwordEncoder, jwtService, userRepository);
    }

    @Test
    @DisplayName("addUser rejects duplicate usernames before persistence")
    void addUserRejectsDuplicateUsernameBeforePersistence() {
        when(userRepository.existsByUsernameIgnoreCase("matei")).thenReturn(true);

        assertThatThrownBy(() -> userService.addUser("matei", "matei@example.com", "Password123A"))
                .isInstanceOf(UserRegistrationConflictException.class)
                .hasMessage("This username is already taken.");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("addUser translates database uniqueness conflicts into domain conflicts")
    void addUserTranslatesDatabaseUniquenessConflictsIntoDomainConflicts() {
        when(userRepository.existsByUsernameIgnoreCase("matei")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("matei@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123A")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "insert failed",
                        new RuntimeException("duplicate key value violates unique constraint \"users_username_key\"")
                ));

        assertThatThrownBy(() -> userService.addUser("matei", "matei@example.com", "Password123A"))
                .isInstanceOf(UserRegistrationConflictException.class)
                .hasMessage("This username is already taken.");
    }
}

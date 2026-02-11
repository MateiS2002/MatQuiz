package ro.mateistanescu.matquizspringbootbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.mateistanescu.matquizspringbootbackend.configuration.SecurityConfig;
import ro.mateistanescu.matquizspringbootbackend.entity.User;
import ro.mateistanescu.matquizspringbootbackend.enums.Role;
import ro.mateistanescu.matquizspringbootbackend.exception.UserRegistrationConflictException;
import ro.mateistanescu.matquizspringbootbackend.filter.JwtFilter;
import ro.mateistanescu.matquizspringbootbackend.handlers.AccessDeniedHandlerImpl;
import ro.mateistanescu.matquizspringbootbackend.handlers.AuthExceptionHandler;
import ro.mateistanescu.matquizspringbootbackend.handlers.GenericControllerAdvice;
import ro.mateistanescu.matquizspringbootbackend.service.GameService;
import ro.mateistanescu.matquizspringbootbackend.service.UserService;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, TestAuthorizationController.class})
@Import({SecurityConfig.class, JwtFilter.class, AuthExceptionHandler.class, AccessDeniedHandlerImpl.class, GenericControllerAdvice.class})
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private GameService gameService;

    @Test
    @DisplayName("POST /api/auth/login returns token for valid credentials")
    void loginReturnsTokenForValidCredentials() throws Exception {
        when(userService.authenticate("john", "secret")).thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of("username", "john", "password", "secret"))))
                .andExpect(status().isOk())
                .andExpect(content().string("jwt-token"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 for invalid credentials")
    void loginReturnsUnauthorizedForInvalidCredentials() throws Exception {
        when(userService.authenticate("john", "wrong"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of("username", "john", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 for invalid payload")
    void loginReturnsBadRequestForInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of("username", "", "password", "secret"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 for duplicate username")
    void registerReturnsConflictForDuplicateUsername() throws Exception {
        doThrow(new UserRegistrationConflictException("This username is already taken."))
                .when(userService).addUser("matei", "matei@example.com", "Password123A");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of(
                                "username", "matei",
                                "email", "matei@example.com",
                                "password", "Password123A"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This username is already taken."));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 for invalid payload")
    void registerReturnsBadRequestForInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of(
                                "username", "john",
                                "email", "invalid-email",
                                "password", "Password123A"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/auth/session returns 401 when token is missing")
    void sessionReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized request"));
    }

    @Test
    @DisplayName("GET /api/auth/session returns user summary for valid token")
    void sessionReturnsUserSummaryForValidToken() throws Exception {
        User user = buildUser("john", "john@example.com", Role.ROLE_USER);
        when(userService.validateUser("valid-user-token")).thenReturn(user);
        when(userService.findByUsername("john")).thenReturn(user);

        mockMvc.perform(get("/api/auth/session")
                        .header("Authorization", "Bearer valid-user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("PATCH /api/auth/changeUsername returns refreshed session for valid token")
    void changeUsernameReturnsRefreshedSessionForValidToken() throws Exception {
        User currentUser = buildUser("john", "john@example.com", Role.ROLE_USER);
        User updatedUser = buildUser("johnny", "john@example.com", Role.ROLE_USER);

        when(userService.validateUser("valid-user-token")).thenReturn(currentUser);
        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.changeUsername(currentUser, "johnny")).thenReturn(updatedUser);
        when(userService.issueSessionToken("johnny")).thenReturn("refreshed-token");

        mockMvc.perform(patch("/api/auth/changeUsername")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of("newUsername", "johnny"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("refreshed-token"))
                .andExpect(jsonPath("$.user.username").value("johnny"))
                .andExpect(jsonPath("$.user.email").value("john@example.com"));
    }

    @Test
    @DisplayName("PATCH /api/auth/changeUsername returns 400 for invalid payload")
    void changeUsernameReturnsBadRequestForInvalidPayload() throws Exception {
        User currentUser = buildUser("john", "john@example.com", Role.ROLE_USER);
        when(userService.validateUser("valid-user-token")).thenReturn(currentUser);

        mockMvc.perform(patch("/api/auth/changeUsername")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of("newUsername", "ab"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/auth/changeEmail returns refreshed session for valid token")
    void changeEmailReturnsRefreshedSessionForValidToken() throws Exception {
        User currentUser = buildUser("john", "john@example.com", Role.ROLE_USER);
        User updatedUser = buildUser("john", "johnny@example.com", Role.ROLE_USER);

        when(userService.validateUser("valid-user-token")).thenReturn(currentUser);
        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.changeEmail(currentUser, "johnny@example.com")).thenReturn(updatedUser);
        when(userService.issueSessionToken("john")).thenReturn("refreshed-token");

        mockMvc.perform(patch("/api/auth/changeEmail")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of("newEmail", "johnny@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("refreshed-token"))
                .andExpect(jsonPath("$.user.username").value("john"))
                .andExpect(jsonPath("$.user.email").value("johnny@example.com"));
    }

    @Test
    @DisplayName("PATCH /api/auth/changeEmail returns 400 for invalid payload")
    void changeEmailReturnsBadRequestForInvalidPayload() throws Exception {
        User currentUser = buildUser("john", "john@example.com", Role.ROLE_USER);
        when(userService.validateUser("valid-user-token")).thenReturn(currentUser);

        mockMvc.perform(patch("/api/auth/changeEmail")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of("newEmail", "invalid-email"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/auth/changePassword returns refreshed session for valid token")
    void changePasswordReturnsRefreshedSessionForValidToken() throws Exception {
        User currentUser = buildUser("john", "john@example.com", Role.ROLE_USER);

        when(userService.validateUser("valid-user-token")).thenReturn(currentUser);
        when(userService.findByUsername("john")).thenReturn(currentUser);
        when(userService.changePassword(currentUser, "secretOld", "secretNew123A")).thenReturn(currentUser);
        when(userService.issueSessionToken("john")).thenReturn("refreshed-token");

        mockMvc.perform(patch("/api/auth/changePassword")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of(
                                "currentPassword", "secretOld",
                                "newPassword", "secretNew123A"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("refreshed-token"))
                .andExpect(jsonPath("$.user.username").value("john"))
                .andExpect(jsonPath("$.user.email").value("john@example.com"));
    }

    @Test
    @DisplayName("PATCH /api/auth/changePassword returns 400 for invalid payload")
    void changePasswordReturnsBadRequestForInvalidPayload() throws Exception {
        User currentUser = buildUser("john", "john@example.com", Role.ROLE_USER);
        when(userService.validateUser("valid-user-token")).thenReturn(currentUser);

        mockMvc.perform(patch("/api/auth/changePassword")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of(
                                "currentPassword", "secretOld",
                                "newPassword", "short"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/auth/setProfilePicture returns 400 for invalid payload")
    void setProfilePictureReturnsBadRequestForInvalidPayload() throws Exception {
        User currentUser = buildUser("john", "john@example.com", Role.ROLE_USER);
        when(userService.validateUser("valid-user-token")).thenReturn(currentUser);

        mockMvc.perform(patch("/api/auth/setProfilePicture")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(Map.of("avatarUrl", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/test/admin returns 403 for authenticated non-admin users")
    void adminEndpointReturnsForbiddenForUserRole() throws Exception {
        User user = buildUser("john", "john@example.com", Role.ROLE_USER);
        when(userService.validateUser("valid-user-token")).thenReturn(user);

        mockMvc.perform(get("/api/test/admin")
                        .header("Authorization", "Bearer valid-user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @DisplayName("GET /api/test/admin returns 200 for admin users")
    void adminEndpointReturnsOkForAdminRole() throws Exception {
        User user = buildUser("admin", "admin@example.com", Role.ROLE_ADMIN);
        when(userService.validateUser("valid-admin-token")).thenReturn(user);

        mockMvc.perform(get("/api/test/admin")
                        .header("Authorization", "Bearer valid-admin-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-ok"));
    }

    private String jsonBody(Map<String, String> values) throws Exception {
        return objectMapper.writeValueAsString(new HashMap<>(values));
    }

    private User buildUser(String username, String email, Role role) {
        return User.builder()
                .id(1L)
                .username(username)
                .email(email)
                .passwordHash("encoded")
                .role(role)
                .build();
    }
}

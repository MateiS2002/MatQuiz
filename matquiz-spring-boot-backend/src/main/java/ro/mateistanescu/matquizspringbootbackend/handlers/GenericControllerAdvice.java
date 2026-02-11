package ro.mateistanescu.matquizspringbootbackend.handlers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ro.mateistanescu.matquizspringbootbackend.exception.UserRegistrationConflictException;

import java.util.Map;

@RestControllerAdvice
public class GenericControllerAdvice {

    @ExceptionHandler(exception = UserRegistrationConflictException.class)
    public ResponseEntity<Map<String, String>> handleUserRegistrationConflictException(UserRegistrationConflictException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(exception = UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUsernameNotFoundException(UsernameNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", e.getMessage()));
    }
}

package ro.mateistanescu.matquizspringbootbackend.exception;

public class UserRegistrationConflictException extends RuntimeException {

    public UserRegistrationConflictException(String message) {
        super(message);
    }
}

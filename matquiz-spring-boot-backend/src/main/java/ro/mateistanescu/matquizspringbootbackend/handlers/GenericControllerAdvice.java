package ro.mateistanescu.matquizspringbootbackend.handlers;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;

@ControllerAdvice
public class GenericControllerAdvice {
    @ResponseStatus(value = HttpStatus.FORBIDDEN)
    @ExceptionHandler(exception = UsernameNotFoundException.class)
    public void handleUsernameNotFoundException(HttpServletResponse response, UsernameNotFoundException e) throws IOException {
        response.getWriter().write(e.getMessage());
    }
}
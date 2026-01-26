package ro.mateistanescu.matquizspringbootbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ro.mateistanescu.matquizspringbootbackend.entity.User;

import ro.mateistanescu.matquizspringbootbackend.repository.UserRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class FailedAnswerService {
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public void sendFailedAnswerMessageToUser(Long userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        log.info("Sending failed answer message to user {}", userId);

        messagingTemplate.convertAndSendToUser(
                user.getUsername(),
                "/queue/failed_answer",
                "FAILED ANSWER"
                );
    }
}

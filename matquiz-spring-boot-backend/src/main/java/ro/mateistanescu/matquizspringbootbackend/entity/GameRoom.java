package ro.mateistanescu.matquizspringbootbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import ro.mateistanescu.matquizspringbootbackend.enums.Difficulty;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@Table(name = "game_rooms")
public class GameRoom {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", unique = true, nullable = false, length = 5)
    private String roomCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Column(nullable = false, length = 100)
    private String topic;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private Difficulty difficulty = Difficulty.EASY;

    @Column(name ="question_count", nullable = false)
    private Integer questionCount = 5;

    @Column(name = "correlation_id", unique = true, nullable = false)
    private UUID correlationId;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private GameStatus status = GameStatus.WAITING;

    @Column(name = "current_question_index", nullable = false)
    private Integer currentQuestionIndex = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "gameRoom", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("orderIndex ASC")
    private List<Question> questions;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        //TODO: See if this check is necessary
        if(this.questionCount == null) this.questionCount = 5;
        if(this.currentQuestionIndex == null) this.currentQuestionIndex = 0;
        if (this.status == null) this.status = GameStatus.WAITING;
        if (this.difficulty == null) this.difficulty = Difficulty.EASY;
    }
}

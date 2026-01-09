package ro.mateistanescu.matquizspringbootbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import ro.mateistanescu.matquizspringbootbackend.enums.Difficulty;
import ro.mateistanescu.matquizspringbootbackend.enums.GameStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Column(length = 100, nullable = false)
    private String topic;

    @Enumerated(EnumType.ORDINAL)
    @Builder.Default
    @Column(nullable = false)
    private Difficulty difficulty = Difficulty.EASY;

    @Builder.Default
    @Column(name ="question_count", nullable = false)
    private Integer questionCount = 5;

    @Column(name = "correlation_id", unique = true)
    private UUID correlationId;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private GameStatus status = GameStatus.WAITING;

    @Builder.Default
    @Column(name = "current_question_index", nullable = false)
    private Integer currentQuestionIndex = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "gameRoom", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<GamePlayer> players = new ArrayList<>();

    public void addPlayer(GamePlayer player) {
        if (this.players == null) this.players = new ArrayList<>();
        this.players.add(player);
        player.setGameRoom(this);
    }

    @OneToMany(mappedBy = "gameRoom", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("orderIndex ASC")
    private List<Question> questions;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();

        // Safety Defaults
        if (this.questionCount == null) this.questionCount = 5;
        if (this.currentQuestionIndex == null) this.currentQuestionIndex = 0;
        if (this.status == null) this.status = GameStatus.WAITING;
        if (this.difficulty == null) this.difficulty = Difficulty.EASY;
        if (this.topic == null) this.topic = "No input";

        if (this.correlationId == null) {
            this.correlationId = UUID.randomUUID();
        }
    }
}

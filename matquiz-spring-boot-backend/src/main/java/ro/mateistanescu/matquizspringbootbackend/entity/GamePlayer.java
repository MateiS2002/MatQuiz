package ro.mateistanescu.matquizspringbootbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_players", uniqueConstraints = {@UniqueConstraint(columnNames = {"game_room_id", "user_id"})})
@Data
public class GamePlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_room_id", nullable = false)
    private GameRoom gameRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "socket_session_id")
    private String socketSessionId;

    @Column(nullable = false)
    private Integer score = 0;

    @Column(name ="is_connected", nullable = false)
    private Boolean isConnected = true;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();

        //TODO: See if this check is necessary
        if (this.score == null) this.score = 0;
        if (this.isConnected == null) this.isConnected = true;
    }
}

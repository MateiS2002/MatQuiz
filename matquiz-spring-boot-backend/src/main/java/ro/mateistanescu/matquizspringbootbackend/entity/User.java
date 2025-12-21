package ro.mateistanescu.matquizspringbootbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "avatar_url", nullable = true, length = 255)
    private String avatarUrl;

    @Column(name = "elo_rating", nullable = false)
    private Integer eloRating = 1000;

    @Column(name = "total_games_played", nullable = false)
    private Integer totalGamesPlayed = 0;

    @Column(name = "last_game_points", nullable = false)
    private Integer lastGamePoints = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "host", fetch = FetchType.LAZY)
    private List<GameRoom> hostedGameRooms;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<GamePlayer> joinedGameRooms;

    @PrePersist
    protected void onCreate() {
        updatedAt = createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

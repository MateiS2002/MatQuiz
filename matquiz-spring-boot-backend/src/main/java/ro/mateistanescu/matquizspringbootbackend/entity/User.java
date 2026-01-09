package ro.mateistanescu.matquizspringbootbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import ro.mateistanescu.matquizspringbootbackend.enums.Role;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "users")
public class User implements Principal {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.ROLE_USER;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "avatar_url", nullable = true, length = 255)
    private String avatarUrl;

    @Builder.Default
    @Column(name = "elo_rating", nullable = false)
    private Integer eloRating = 1000;

    @Builder.Default
    @Column(name = "total_games_played", nullable = false)
    private Integer totalGamesPlayed = 0;

    @Builder.Default
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

    @Override
    public String getName() {
        return this.username;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

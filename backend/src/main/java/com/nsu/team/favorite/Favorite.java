package com.nsu.team.favorite;

import com.nsu.team.post.SalePost;
import com.nsu.team.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Getter
@Entity
@Table(name = "favorites", indexes = @Index(name = "idx_favorites_user_created", columnList = "user_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {
    @EmbeddedId
    private FavoriteId id;
    @MapsId("userId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @MapsId("postId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private SalePost post;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Favorite(User user, SalePost post) {
        this.id = new FavoriteId(user.getId(), post.getId());
        this.user = user;
        this.post = post;
    }

    @PrePersist void created() { createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }
}

package com.nsu.team.favorite;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class FavoriteId implements Serializable {
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "post_id")
    private Long postId;

    protected FavoriteId() {}
    public FavoriteId(Long userId, Long postId) {
        this.userId = userId;
        this.postId = postId;
    }
    public Long getUserId() { return userId; }
    public Long getPostId() { return postId; }

    @Override public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof FavoriteId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(postId, that.postId);
    }
    @Override public int hashCode() { return Objects.hash(userId, postId); }
}

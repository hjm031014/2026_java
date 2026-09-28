package com.nsu.team.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "post_view_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_post_viewer", columnNames = {"post_id", "viewer_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostViewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private SalePost post;

    @Column(name = "viewer_key", nullable = false, length = 80)
    private String viewerKey;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    public PostViewEvent(SalePost post, String viewerKey, Instant viewedAt) {
        this.post = post;
        this.viewerKey = viewerKey;
        this.viewedAt = viewedAt;
    }

    public void viewedAt(Instant viewedAt) {
        this.viewedAt = viewedAt;
    }
}

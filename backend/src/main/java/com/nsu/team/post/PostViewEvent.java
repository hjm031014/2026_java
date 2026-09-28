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
@Table(name = "post_view_events", uniqueConstraints = {
		@UniqueConstraint(name = "uk_post_viewer", columnNames = {"post_id", "viewer_key"}),
		@UniqueConstraint(name = "uk_post_viewer_hash", columnNames = {"post_id", "viewer_key_hash"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostViewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private SalePost post;

	@Column(name = "viewer_user_id")
	private Long viewerUserId;

	@Column(name = "viewer_key_hash", nullable = false, length = 128)
	private String viewerKeyHash;

	@Column(name = "first_viewed_at", nullable = false)
	private Instant firstViewedAt;

	@Column(name = "last_counted_at", nullable = false)
	private Instant lastCountedAt;

	/** 기존 Neon 컬럼과의 하위 호환용. 신규 중복 판정에는 viewerKeyHash를 사용합니다. */
    @Column(name = "viewer_key", nullable = false, length = 80)
    private String viewerKey;

	/** 기존 Neon 컬럼과의 하위 호환용. lastCountedAt과 같은 값을 기록합니다. */
    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

	public PostViewEvent(SalePost post, Long viewerUserId, String viewerKeyHash, Instant viewedAt) {
        this.post = post;
		this.viewerUserId = viewerUserId;
		this.viewerKeyHash = viewerKeyHash;
		this.firstViewedAt = viewedAt;
		this.lastCountedAt = viewedAt;
		this.viewerKey = viewerKeyHash;
        this.viewedAt = viewedAt;
    }

	public void countedAt(Instant viewedAt) {
		this.lastCountedAt = viewedAt;
        this.viewedAt = viewedAt;
    }
}

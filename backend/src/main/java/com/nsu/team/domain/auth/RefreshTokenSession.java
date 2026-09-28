package com.nsu.team.domain.auth;

import com.nsu.team.common.entity.BaseTimeEntity;
import com.nsu.team.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ERD REFRESH_TOKEN_SESSIONS 테이블. 리프레시 토큰의 평문은 저장하지 않고
 * SHA-256 해시(token_hash)만 저장합니다.
 *
 * <p>로그인마다 새 세션(행)을 만들고, 갱신할 때마다 같은 행의 token_hash 를 교체(rotate)합니다.
 * 이미 교체되어 무효화된(rotate 이전) 토큰이 다시 들어오면 재사용 공격으로 간주해 세션을 폐기합니다.
 */
@Entity
@Table(name = "refresh_token_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenSession extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 128)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Builder
	private RefreshTokenSession(User user, String tokenHash, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired() {
		return expiresAt.isBefore(Instant.now());
	}

	public boolean matchesHash(String candidateHash) {
		return this.tokenHash.equals(candidateHash);
	}

	public void rotate(String newTokenHash, Instant newExpiresAt) {
		this.tokenHash = newTokenHash;
		this.expiresAt = newExpiresAt;
		this.lastUsedAt = Instant.now();
	}

	public void revoke() {
		this.revokedAt = Instant.now();
	}
}

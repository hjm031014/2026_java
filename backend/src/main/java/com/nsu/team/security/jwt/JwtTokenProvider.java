package com.nsu.team.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 액세스 토큰(JWT) 발급/검증. 리프레시 토큰은 JWT 가 아닌 불투명 토큰이므로
 * {@link com.nsu.team.domain.auth.RefreshTokenService} 에서 별도로 처리합니다.
 */
@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final long accessTokenValiditySeconds;

	public JwtTokenProvider(JwtProperties jwtProperties) {
		this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
		this.accessTokenValiditySeconds = jwtProperties.getAccessTokenValiditySeconds();
	}

	public String generateAccessToken(Long userId, String email, String nickname) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("email", email)
				.claim("nickname", nickname)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(accessTokenValiditySeconds)))
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	public Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public long getAccessTokenValiditySeconds() {
		return accessTokenValiditySeconds;
	}
}

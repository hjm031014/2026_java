package com.nsu.team.domain.auth;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.domain.user.User;
import com.nsu.team.security.CookieProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 리프레시 토큰 발급/검증/폐기(로그아웃) 및 쿠키 관리.
 *
 * <p>쿠키 값 형식: {@code "<세션ID>.<랜덤시크릿>"}. 세션 ID 로 DB 행을 찾고,
 * 시크릿을 해시한 값이 저장된 token_hash 와 일치하는지 확인합니다.
 * 일치하지 않는데 세션이 아직 살아있다면(=이미 rotate 되어 무효화된 토큰이 재전송된 것) 재사용 공격이므로
 * 해당 세션 전체를 폐기(revoke)하고 REFRESH_TOKEN_REUSED 를 반환합니다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private static final String COOKIE_NAME = "refreshToken";
	private static final String COOKIE_PATH = "/api/v1/auth";
	private static final Duration VALIDITY = Duration.ofDays(7);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenSessionRepository refreshTokenSessionRepository;
	private final CookieProperties cookieProperties;

	@Transactional
	public void issueNewSession(User user, HttpServletResponse response) {
		String secret = generateSecret();
		RefreshTokenSession session = RefreshTokenSession.builder()
				.user(user)
				.tokenHash(hash(secret))
				.expiresAt(Instant.now().plus(VALIDITY))
				.build();
		refreshTokenSessionRepository.save(session);

		addCookie(response, session.getId(), secret);
	}

	@Transactional
	public RefreshTokenSession validateAndRotate(String cookieValue, HttpServletResponse response) {
		ParsedToken parsed = parse(cookieValue);
		RefreshTokenSession session = refreshTokenSessionRepository.findById(parsed.sessionId())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		if (session.isRevoked()) {
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
		}
		if (session.isExpired()) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		if (!session.matchesHash(hash(parsed.secret()))) {
			session.revoke();
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
		}

		String newSecret = generateSecret();
		session.rotate(hash(newSecret), Instant.now().plus(VALIDITY));
		addCookie(response, session.getId(), newSecret);

		return session;
	}

	@Transactional
	public void revoke(String cookieValue) {
		if (cookieValue == null) {
			return;
		}
		parseSafely(cookieValue)
				.flatMap(parsed -> refreshTokenSessionRepository.findById(parsed.sessionId()))
				.ifPresent(RefreshTokenSession::revoke);
	}

	public void expireCookie(HttpServletResponse response) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, "")
				.httpOnly(true)
				.secure(cookieProperties.isSecure())
				.sameSite(cookieProperties.getSameSite())
				.path(COOKIE_PATH)
				.maxAge(0);
		if (cookieProperties.hasDomain()) {
			builder.domain(cookieProperties.getDomain());
		}
		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
	}

	private void addCookie(HttpServletResponse response, Long sessionId, String secret) {
		String value = sessionId + "." + secret;
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
				.httpOnly(true)
				.secure(cookieProperties.isSecure())
				.sameSite(cookieProperties.getSameSite())
				.path(COOKIE_PATH)
				.maxAge(VALIDITY);
		if (cookieProperties.hasDomain()) {
			builder.domain(cookieProperties.getDomain());
		}
		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
	}

	private record ParsedToken(Long sessionId, String secret) {
	}

	private ParsedToken parse(String cookieValue) {
		return parseSafely(cookieValue).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
	}

	private java.util.Optional<ParsedToken> parseSafely(String cookieValue) {
		if (cookieValue == null || cookieValue.isBlank()) {
			return java.util.Optional.empty();
		}
		int dotIndex = cookieValue.indexOf('.');
		if (dotIndex <= 0 || dotIndex == cookieValue.length() - 1) {
			return java.util.Optional.empty();
		}
		try {
			Long sessionId = Long.valueOf(cookieValue.substring(0, dotIndex));
			String secret = cookieValue.substring(dotIndex + 1);
			return java.util.Optional.of(new ParsedToken(sessionId, secret));
		} catch (NumberFormatException e) {
			return java.util.Optional.empty();
		}
	}

	private String generateSecret() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String secret) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
		}
	}
}

package com.nsu.team.security.csrf;

import com.nsu.team.security.CookieProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * GET /auth/csrf 에서 사용하는 CSRF 토큰 발급기(더블 서브밋 쿠키 방식).
 * 토큰은 HttpOnly 쿠키(XSRF-TOKEN)와 JSON 응답 본문(csrfToken) 양쪽에 내려주고,
 * 프론트는 응답 본문 값을 저장해두었다가 이후 요청의 X-CSRF-Token 헤더로 실어 보냅니다.
 * 쿠키가 HttpOnly 라 자바스크립트로 직접 읽을 수 없기 때문에, 두 값이 일치하려면
 * 반드시 우리 서버가 내려준 응답을 읽을 수 있는(=CORS 를 통과한 same-origin 취급) 클라이언트여야 합니다.
 */
@Component
@RequiredArgsConstructor
public class CsrfTokenIssuer {

	public static final String COOKIE_NAME = "XSRF-TOKEN";
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final CookieProperties cookieProperties;

	public String issue(HttpServletResponse response) {
		String token = generateToken();

		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, token)
				.httpOnly(true)
				.secure(cookieProperties.isSecure())
				.sameSite(cookieProperties.getSameSite())
				.path("/api/v1")
				.maxAge(Duration.ofHours(2));
		if (cookieProperties.hasDomain()) {
			builder.domain(cookieProperties.getDomain());
		}

		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
		return token;
	}

	private String generateToken() {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}
}

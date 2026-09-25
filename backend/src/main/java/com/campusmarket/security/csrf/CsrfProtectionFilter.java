package com.campusmarket.security.csrf;

import com.campusmarket.common.exception.ErrorCode;
import com.campusmarket.common.response.ErrorResponse;
import com.campusmarket.common.util.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * API.md 규칙: "회원가입·로그인·갱신·로그아웃·조회 이벤트에는 X-CSRF-Token 사용".
 * 더블 서브밋 쿠키(XSRF-TOKEN) 값과 X-CSRF-Token 헤더 값이 일치하는지만 검사합니다.
 *
 * <p>조회 이벤트(POST /posts/{postId}/views)는 B 파트에서 구현 예정이라 아직 컨트롤러는 없지만,
 * 경로 패턴만 미리 등록해두었습니다.
 */
@Component
@RequiredArgsConstructor
public class CsrfProtectionFilter extends OncePerRequestFilter {

	private static final String HEADER_NAME = "X-CSRF-Token";
	private static final Set<String> PROTECTED_EXACT_PATHS = Set.of(
			"/api/v1/auth/signup",
			"/api/v1/auth/login",
			"/api/v1/auth/refresh",
			"/api/v1/auth/logout"
	);
	private static final Pattern VIEW_EVENT_PATTERN = Pattern.compile("^/api/v1/posts/[^/]+/views$");

	private final ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (requiresCsrfCheck(request)) {
			String cookieToken = readCookieToken(request);
			String headerToken = request.getHeader(HEADER_NAME);

			if (cookieToken == null || headerToken == null || !cookieToken.equals(headerToken)) {
				respondCsrfInvalid(request, response);
				return;
			}
		}

		filterChain.doFilter(request, response);
	}

	private boolean requiresCsrfCheck(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return PROTECTED_EXACT_PATHS.contains(uri) || VIEW_EVENT_PATTERN.matcher(uri).matches();
	}

	private String readCookieToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (CsrfTokenIssuer.COOKIE_NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private void respondCsrfInvalid(HttpServletRequest request, HttpServletResponse response) throws IOException {
		Object requestIdAttr = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
		String requestId = requestIdAttr != null ? requestIdAttr.toString() : UUID.randomUUID().toString();

		response.setStatus(ErrorCode.CSRF_INVALID.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(),
				ErrorResponse.of(ErrorCode.CSRF_INVALID, ErrorCode.CSRF_INVALID.getDefaultMessage(), List.of(), requestId));
	}
}

package com.nsu.team.security.jwt;

import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer &lt;accessToken&gt; 헤더를 검증해 SecurityContext 에 인증 정보를 채웁니다.
 *
 * <p>토큰이 없으면 그냥 다음 필터로 넘기고(공개 API 접근 허용), 토큰이 있지만 만료/위조된 경우에는
 * 예외를 여기서 던지지 않고 요청 속성에 담아두었다가 {@link com.nsu.team.security.RestAuthenticationEntryPoint}
 * 에서 정확한 오류 코드(ACCESS_TOKEN_EXPIRED / INVALID_TOKEN)로 응답합니다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String AUTH_ERROR_ATTRIBUTE = "authError";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length());
			try {
				Claims claims = jwtTokenProvider.parseClaims(token);
				authenticate(claims, request);
			} catch (ExpiredJwtException e) {
				request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.ACCESS_TOKEN_EXPIRED);
			} catch (JwtException | IllegalArgumentException e) {
				request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.INVALID_TOKEN);
			}
		}

		filterChain.doFilter(request, response);
	}

	private void authenticate(Claims claims, HttpServletRequest request) {
		Long userId = Long.valueOf(claims.getSubject());
		String email = claims.get("email", String.class);
		String nickname = claims.get("nickname", String.class);
		UserPrincipal principal = new UserPrincipal(userId, email, nickname);

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(principal, null, List.of());
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}

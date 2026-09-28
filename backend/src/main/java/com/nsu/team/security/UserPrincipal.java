package com.nsu.team.security;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * JWT 액세스 토큰을 파싱해 만든 인증 주체. SecurityContext 의 Authentication#getPrincipal() 로
 * 저장되며, 컨트롤러에서는 {@code @AuthenticationPrincipal UserPrincipal principal} 로 꺼내 씁니다.
 *
 * <p>{@link AuthenticatedPrincipal} 을 구현해 {@code Authentication#getName()} 이 사용자 ID
 * 문자열을 반환하도록 합니다.
 */
public record UserPrincipal(Long userId, String email, String nickname) implements AuthenticatedPrincipal {

	@Override
	public String getName() {
		return String.valueOf(userId);
	}
}

package com.nsu.team.security;

/**
 * JWT 액세스 토큰을 파싱해 만든 인증 주체. SecurityContext 의 Authentication#getPrincipal() 로
 * 저장되며, 컨트롤러에서는 {@code @AuthenticationPrincipal UserPrincipal principal} 로 꺼내 씁니다.
 */
public record UserPrincipal(Long userId, String email, String nickname) {
}

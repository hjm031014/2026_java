package com.nsu.team.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 / CSRF 쿠키 공통 옵션. Vercel(프론트)과 Render(백엔드) 도메인이 다르므로
 * 배포 환경에서는 secure=true, sameSite=None 이 필요합니다. 로컬 개발(http)에서는
 * secure=false, sameSite=Lax 를 사용하세요.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cookie")
public class CookieProperties {

	private boolean secure = true;
	private String sameSite = "None";

	/** 비워두면 쿠키에 Domain 속성을 지정하지 않습니다(현재 호스트 기준). */
	private String domain = "";

	public boolean hasDomain() {
		return domain != null && !domain.isBlank();
	}
}

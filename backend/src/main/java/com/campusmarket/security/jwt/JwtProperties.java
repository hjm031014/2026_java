package com.campusmarket.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

	/** HS256 서명 비밀키. 최소 32바이트(256비트) 이상이어야 합니다. */
	private String secret;

	/** 액세스 토큰 유효 기간(초). 기본 900초(15분). */
	private long accessTokenValiditySeconds = 900;
}

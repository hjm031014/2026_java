package com.nsu.team.config;

import com.nsu.team.security.RestAccessDeniedHandler;
import com.nsu.team.security.RestAuthenticationEntryPoint;
import com.nsu.team.security.csrf.CsrfProtectionFilter;
import com.nsu.team.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT + 리프레시 쿠키 기반 stateless 인증 설정.
 *
 * <p>/api/v1/auth/** 와 댓글 목록 조회(비회원도 열람 가능)는 공개이고 그 외 전체는 인증이 필요합니다.
 * 판매글 목록/상세/검색, 카테고리, 거래 장소처럼 비회원도 접근 가능한 공개 API 는
 * B 파트에서 컨트롤러를 추가할 때 이 설정의 authorizeHttpRequests 에 permitAll 규칙을
 * 함께 추가해주세요.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final CsrfProtectionFilter csrfProtectionFilter;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
	private final RestAccessDeniedHandler restAccessDeniedHandler;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint(restAuthenticationEntryPoint)
						.accessDeniedHandler(restAccessDeniedHandler))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/v1/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/posts/*/comments").permitAll()
						// TODO(B): 공개 GET API(판매글 목록/상세/검색, 카테고리, 거래 장소 등) permitAll 추가
						.anyRequest().authenticated())
				.addFilterBefore(csrfProtectionFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}

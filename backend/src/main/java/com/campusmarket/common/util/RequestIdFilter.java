package com.campusmarket.common.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 모든 요청에 요청 ID를 부여해 오류 응답의 requestId 로 사용하고,
 * 응답 헤더(X-Request-Id)로도 내려줘서 로그 추적을 돕습니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String ATTRIBUTE_NAME = "requestId";
	public static final String HEADER_NAME = "X-Request-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = UUID.randomUUID().toString();
		request.setAttribute(ATTRIBUTE_NAME, requestId);
		response.setHeader(HEADER_NAME, requestId);
		filterChain.doFilter(request, response);
	}
}

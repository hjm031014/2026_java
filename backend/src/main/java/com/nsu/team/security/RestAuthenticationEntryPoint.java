package com.nsu.team.security;

import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.common.response.ErrorResponse;
import com.nsu.team.common.util.RequestIdFilter;
import com.nsu.team.security.jwt.JwtAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 인증되지 않은 요청이 인증이 필요한 API 에 접근했을 때 401 응답을 내려줍니다.
 * JwtAuthenticationFilter 가 토큰 만료/위조를 감지한 경우 그 사유를 그대로 사용하고,
 * 토큰 자체가 없었다면 UNAUTHENTICATED 로 응답합니다.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		ErrorCode errorCode = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
		if (errorCode == null) {
			errorCode = ErrorCode.UNAUTHENTICATED;
		}

		Object requestIdAttr = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
		String requestId = requestIdAttr != null ? requestIdAttr.toString() : UUID.randomUUID().toString();

		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(),
				ErrorResponse.of(errorCode, errorCode.getDefaultMessage(), List.of(), requestId));
	}
}

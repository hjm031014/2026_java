package com.nsu.team.security;

import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.common.response.ErrorResponse;
import com.nsu.team.common.util.RequestIdFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 인증은 되었지만 권한이 없는 요청에 대해 403 FORBIDDEN 으로 응답합니다.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
			throws IOException {
		ErrorCode errorCode = ErrorCode.FORBIDDEN;
		Object requestIdAttr = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
		String requestId = requestIdAttr != null ? requestIdAttr.toString() : UUID.randomUUID().toString();

		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(),
				ErrorResponse.of(errorCode, errorCode.getDefaultMessage(), List.of(), requestId));
	}
}

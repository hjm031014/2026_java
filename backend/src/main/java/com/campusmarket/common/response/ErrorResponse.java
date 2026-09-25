package com.campusmarket.common.response;

import com.campusmarket.common.exception.ErrorCode;

import java.util.List;

/**
 * 모든 오류 응답의 공통 포맷: {@code { "error": { code, message, details, requestId } } }
 */
public record ErrorResponse(ErrorDetail error) {

	public record ErrorDetail(String code, String message, List<String> details, String requestId) {
	}

	public static ErrorResponse of(ErrorCode code, String message, List<String> details, String requestId) {
		return new ErrorResponse(new ErrorDetail(
				code.name(),
				message,
				details == null ? List.of() : details,
				requestId
		));
	}
}

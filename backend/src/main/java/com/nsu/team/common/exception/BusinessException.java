package com.nsu.team.common.exception;

import lombok.Getter;

import java.util.List;

/**
 * 도메인 규칙 위반 시 던지는 공통 예외. GlobalExceptionHandler 가
 * ErrorCode 에 정의된 HTTP 상태/메시지로 변환합니다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	private final List<String> details;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.getDefaultMessage(), List.of());
	}

	public BusinessException(ErrorCode errorCode, String message) {
		this(errorCode, message, List.of());
	}

	public BusinessException(ErrorCode errorCode, String message, List<String> details) {
		super(message);
		this.errorCode = errorCode;
		this.details = details;
	}
}

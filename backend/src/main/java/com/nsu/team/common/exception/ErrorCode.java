package com.nsu.team.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API.md 의 오류 코드 표와 1:1로 대응합니다. 다른 파트(B, C)도 이 enum 을 그대로 사용합니다.
 */
@Getter
public enum ErrorCode {

	// 400
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서 값이 올바르지 않습니다."),

	// 401
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
	TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "폐기된 토큰입니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
	REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "이미 사용된 리프레시 토큰이 재사용되었습니다."),

	// 403
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF 토큰이 유효하지 않습니다."),

	// 404
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원을 찾을 수 없습니다."),

	// 409
	VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 요청에 의해 이미 변경되었습니다."),
	INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 상태 전이입니다."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	IMAGE_ALREADY_ATTACHED(HttpStatus.CONFLICT, "이미 다른 글에 첨부된 이미지입니다."),
	IMAGE_IN_USE(HttpStatus.CONFLICT, "글에 첨부된 이미지는 삭제할 수 없습니다."),
	IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "동일한 요청 키가 이미 처리되었습니다."),

	// 413 / 415
	PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일 용량이 너무 큽니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식입니다."),

	// 429 / 500
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}
}

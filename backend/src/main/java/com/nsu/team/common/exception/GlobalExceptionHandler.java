package com.nsu.team.common.exception;

import com.nsu.team.common.response.ErrorResponse;
import com.nsu.team.common.util.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.UUID;

/**
 * 모든 컨트롤러의 예외를 API.md 의 공통 오류 포맷으로 변환합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {
		log.warn("BusinessException: code={}, message={}", e.getErrorCode(), e.getMessage());
		return build(e.getErrorCode(), e.getMessage(), e.getDetails(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
		List<String> details = e.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
				.toList();
		return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), details, request);
	}

	@ExceptionHandler({
			HttpMessageNotReadableException.class,
			MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class,
			ConstraintViolationException.class,
			MissingServletRequestPartException.class,
			HttpRequestMethodNotSupportedException.class,
	})
	public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
		return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), List.of(), request);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
		return build(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.getDefaultMessage(), List.of(), request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
		return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage(), List.of(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("Unhandled exception", e);
		return build(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage(), List.of(), request);
	}

	private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, List<String> details, HttpServletRequest request) {
		String requestId = resolveRequestId(request);
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, message, details, requestId));
	}

	private String resolveRequestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
		return requestId != null ? requestId.toString() : UUID.randomUUID().toString();
	}
}

package com.nsu.team.common.response;

/**
 * 모든 성공 응답의 공통 포맷: {@code { "data": ... } }
 */
public record ApiResponse<T>(T data) {

	public static <T> ApiResponse<T> of(T data) {
		return new ApiResponse<>(data);
	}
}

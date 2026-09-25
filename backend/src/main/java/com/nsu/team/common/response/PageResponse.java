package com.nsu.team.common.response;

import java.util.List;

/**
 * 목록 조회 API 공통 응답 포맷: {@code { "items": [...], "page": { nextCursor, hasNext } } }.
 * 판매글/댓글/채팅 등 다른 파트에서도 동일하게 사용합니다.
 */
public record PageResponse<T>(List<T> items, PageInfo page) {

	public static <T> PageResponse<T> of(List<T> items, String nextCursor, boolean hasNext) {
		return new PageResponse<>(items, new PageInfo(nextCursor, hasNext));
	}
}

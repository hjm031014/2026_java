package com.campusmarket.common.response;

/**
 * 커서 기반 페이지네이션 메타데이터. 마지막 페이지의 nextCursor 는 null.
 */
public record PageInfo(String nextCursor, boolean hasNext) {
}

package com.nsu.team.common.util;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 커서 기반 페이지네이션에서 사용할 공통 커서 인코딩 유틸.
 * 판매글/댓글/찜/채팅 목록 조회(B, C 파트)에서 재사용하세요.
 *
 * <p>커서는 정렬 기준 값을 그대로 노출하지 않도록 Base64 로 감싼 불투명(opaque) 문자열입니다.
 * 정렬 기준이 여러 개(예: createdAt + id)라면 {@code "createdAt:id"} 형태로 합친 뒤 인코딩하세요.
 */
public final class CursorCodec {

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private CursorCodec() {
	}

	public static String encode(String rawValue) {
		return ENCODER.encodeToString(rawValue.getBytes(StandardCharsets.UTF_8));
	}

	public static String decode(String cursor) {
		try {
			return new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_CURSOR);
		}
	}

	public static Long decodeAsId(String cursor) {
		try {
			return Long.valueOf(decode(cursor));
		} catch (NumberFormatException e) {
			throw new BusinessException(ErrorCode.INVALID_CURSOR);
		}
	}

	public static String encode(Instant time, long id) {
		String raw = time.truncatedTo(ChronoUnit.MICROS) + "|" + id;
		return encode(raw);
	}

	public static KeysetCursor decodeKeyset(String cursor) {
		try {
			String[] values = decode(cursor).split("\\|", -1);
			if (values.length != 2) throw new IllegalArgumentException();
			return new KeysetCursor(Instant.parse(values[0]), Long.parseLong(values[1]));
		} catch (RuntimeException exception) {
			if (exception instanceof BusinessException businessException) throw businessException;
			throw new BusinessException(ErrorCode.INVALID_CURSOR);
		}
	}

	public static KeysetCursor decodeKeysetNullable(String cursor) {
		return cursor == null || cursor.isBlank() ? null : decodeKeyset(cursor);
	}

	public record KeysetCursor(Instant time, long id) {
	}
}

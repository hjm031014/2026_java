package com.nsu.team.domain.user.dto;

import com.nsu.team.domain.user.User;

import java.time.format.DateTimeFormatter;

/**
 * API.md 의 MyUser 스키마. DB 는 BIGINT ID 를 쓰지만, API 계약(id: string)에 맞춰
 * 문자열로 변환해 내려줍니다.
 */
public record MyUserResponse(String id, String email, String nickname, String createdAt) {

	public static MyUserResponse from(User user) {
		return new MyUserResponse(
				String.valueOf(user.getId()),
				user.getEmail(),
				user.getNickname(),
				DateTimeFormatter.ISO_INSTANT.format(user.getCreatedAt())
		);
	}
}

package com.nsu.team.user;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * JWT principal의 사용자 ID를 실제 사용자 엔티티로 변환합니다.
 * 통합 테스트에서 이메일을 principal로 사용하는 경우도 지원합니다.
 */
@Component
public class CurrentUserProvider {
	private final UserRepository users;

	public CurrentUserProvider(UserRepository users) {
		this.users = users;
	}

	public User require() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			throw BusinessException.unauthenticated();
		}

		String name = authentication.getName();
		try {
			return users.findById(Long.parseLong(name)).orElseThrow(BusinessException::unauthenticated);
		} catch (NumberFormatException ignored) {
			return users.findByEmail(name).orElseThrow(BusinessException::unauthenticated);
		}
	}

	public User requireForUpdate() {
		User current = require();
		return users.findByIdForUpdate(current.getId()).orElseThrow(BusinessException::unauthenticated);
	}
}

package com.nsu.team.user;

import com.nsu.team.common.ApiException;
import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 현재 인증된 사용자를 조회합니다. {@code authentication.getName()} 은 두 가지 경우를 모두 지원합니다:
 * 실제 JWT 로그인 시에는 {@link com.nsu.team.security.UserPrincipal} 이 principal 이며
 * {@code getName()} 이 숫자 사용자 ID 문자열을 반환하고, 테스트에서 이메일 문자열을 직접
 * principal 로 넣는 경우에는 이메일로 조회합니다.
 */
@Component
public class CurrentUserProvider {
    private final UserRepository users;

    public CurrentUserProvider(UserRepository users) { this.users = users; }

    public User require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw unauthenticated();
        }
        String name = authentication.getName();
        try {
            return users.findById(Long.parseLong(name)).orElseThrow(this::unauthenticated);
        } catch (NumberFormatException ignored) {
            return users.findByEmail(name).orElseThrow(this::unauthenticated);
        }
    }

    private ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "인증이 필요합니다.");
    }
}

package com.nsu.team.user;

import com.nsu.team.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {
    private final UserAccountRepository users;

    public CurrentUserProvider(UserAccountRepository users) { this.users = users; }

    public UserAccount require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "인증이 필요합니다.");
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

package com.campusmarket.domain.auth.dto;

import com.campusmarket.domain.user.dto.MyUserResponse;

public record LoginResponse(MyUserResponse user, String accessToken, String tokenType, long expiresIn) {
}

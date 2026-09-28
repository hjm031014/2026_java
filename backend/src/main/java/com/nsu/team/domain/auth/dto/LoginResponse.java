package com.nsu.team.domain.auth.dto;

import com.nsu.team.domain.user.dto.MyUserResponse;

public record LoginResponse(MyUserResponse user, String accessToken, String tokenType, long expiresIn) {
}

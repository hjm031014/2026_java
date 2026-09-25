package com.nsu.team.domain.auth.dto;

public record TokenRefreshResponse(String accessToken, String tokenType, long expiresIn) {
}

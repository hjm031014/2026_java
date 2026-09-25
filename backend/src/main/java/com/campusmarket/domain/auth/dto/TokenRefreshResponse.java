package com.campusmarket.domain.auth.dto;

public record TokenRefreshResponse(String accessToken, String tokenType, long expiresIn) {
}

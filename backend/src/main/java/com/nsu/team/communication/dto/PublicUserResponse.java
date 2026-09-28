package com.nsu.team.communication.dto;

import com.nsu.team.domain.user.User;

public record PublicUserResponse(String id, String nickname) {
    public static PublicUserResponse from(User user) {
        return new PublicUserResponse(user.getId().toString(), user.getNickname());
    }
}

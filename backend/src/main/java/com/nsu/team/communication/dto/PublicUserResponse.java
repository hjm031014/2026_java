package com.nsu.team.communication.dto;

import com.nsu.team.user.UserAccount;

public record PublicUserResponse(String id, String nickname) {
    public static PublicUserResponse from(UserAccount user) {
        return new PublicUserResponse(user.getId().toString(), user.getNickname());
    }
}

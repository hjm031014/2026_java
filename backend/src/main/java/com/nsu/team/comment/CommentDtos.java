package com.nsu.team.comment;

import com.nsu.team.communication.dto.PublicUserResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CommentDtos {
    private CommentDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 1000) String content
    ) {}

    public record Response(
            String id,
            String postId,
            PublicUserResponse author,
            String content,
            Instant createdAt
    ) {
        static Response from(Comment comment) {
            return new Response(
                    comment.getId().toString(),
                    comment.getPost().getId().toString(),
                    PublicUserResponse.from(comment.getAuthor()),
                    comment.getContent(),
                    comment.getCreatedAt());
        }
    }
}

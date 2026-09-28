package com.nsu.team.comment;

import com.nsu.team.common.response.ApiResponse;
import com.nsu.team.common.response.PageResponse;
import com.nsu.team.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<PageResponse<CommentDtos.Response>> list(
            @PathVariable Long postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.of(commentService.list(postId, cursor, limit));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentDtos.Response>> create(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CommentDtos.CreateRequest request) {
        CommentDtos.Response created = commentService.create(postId, principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/comments/" + created.id()))
                .body(ApiResponse.of(created));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        commentService.delete(commentId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}

package com.nsu.team.comment;

import com.nsu.team.common.response.ApiResponse;
import com.nsu.team.common.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1")
public class CommentController {
    private final CommentService service;

    public CommentController(CommentService service) { this.service = service; }

    @GetMapping("/posts/{postId}/comments")
    ApiResponse<PageResponse<CommentDtos.Response>> list(
            @PathVariable Long postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.of(service.list(postId, cursor, limit));
    }

    @PostMapping("/posts/{postId}/comments")
    ResponseEntity<ApiResponse<CommentDtos.Response>> create(
            @PathVariable Long postId, @Valid @RequestBody CommentDtos.CreateRequest request) {
        CommentDtos.Response created = service.create(postId, request);
        return ResponseEntity.created(URI.create("/api/v1/comments/" + created.id()))
                .body(ApiResponse.of(created));
    }

    @DeleteMapping("/comments/{commentId}")
    ResponseEntity<Void> delete(@PathVariable Long commentId) {
        service.delete(commentId);
        return ResponseEntity.noContent().build();
    }
}

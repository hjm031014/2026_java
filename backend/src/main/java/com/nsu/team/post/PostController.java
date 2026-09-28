package com.nsu.team.post;

import com.nsu.team.common.response.ApiResponse;
import com.nsu.team.common.response.PageResponse;
import com.nsu.team.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<ApiResponse<PostDtos.Detail>> create(
            @Valid @RequestBody PostDtos.CreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(postService.create(request, principal.userId())));
    }

    @GetMapping
    public ApiResponse<PageResponse<PostDtos.Summary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) SalePost.Status status,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.of(postService.list(q, categoryId, status, sort, cursor, limit, userId(principal)));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDtos.Detail> detail(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.of(postService.detail(postId, userId(principal)));
    }

    @PatchMapping("/{postId}")
    public ApiResponse<PostDtos.Detail> update(
            @PathVariable Long postId,
            @Valid @RequestBody PostDtos.UpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.of(postService.update(postId, request, principal.userId()));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long postId,
            @RequestParam @PositiveOrZero long version,
            @AuthenticationPrincipal UserPrincipal principal) {
        postService.delete(postId, version, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{postId}/status")
    public ApiResponse<PostDtos.Detail> changeStatus(
            @PathVariable Long postId,
            @Valid @RequestBody PostDtos.StatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.of(postService.changeStatus(postId, request, principal.userId()));
    }

    @PostMapping("/{postId}/views")
    public ApiResponse<PostDtos.ViewResponse> recordView(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {
        return ApiResponse.of(postService.recordView(postId, userId(principal), clientAddress(request)));
    }

    private Long userId(UserPrincipal principal) {
        return principal == null ? null : principal.userId();
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}

package com.nsu.team.favorite;

import com.nsu.team.common.response.ApiResponse;
import com.nsu.team.common.response.PageResponse;
import com.nsu.team.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;

    @PutMapping("/posts/{postId}/favorite")
    public ResponseEntity<Void> add(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        favoriteService.add(postId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{postId}/favorite")
    public ResponseEntity<Void> remove(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        favoriteService.remove(postId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/favorites")
    public ApiResponse<PageResponse<FavoriteDtos.FavoriteItem>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.of(favoriteService.list(principal.userId(), cursor, limit));
    }
}

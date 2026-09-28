package com.nsu.team.favorite;

import com.nsu.team.common.response.ApiResponse;
import com.nsu.team.common.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1")
public class FavoriteController {
    private final FavoriteService service;

    public FavoriteController(FavoriteService service) { this.service = service; }

    @PutMapping("/posts/{postId}/favorite")
    ResponseEntity<Void> add(@PathVariable Long postId) {
        service.add(postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{postId}/favorite")
    ResponseEntity<Void> remove(@PathVariable Long postId) {
        service.remove(postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/favorites")
    ApiResponse<PageResponse<FavoriteDtos.FavoriteItem>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.of(service.list(cursor, limit));
    }
}

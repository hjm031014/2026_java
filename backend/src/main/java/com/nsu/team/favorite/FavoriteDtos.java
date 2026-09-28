package com.nsu.team.favorite;

import com.nsu.team.communication.dto.PublicUserResponse;
import com.nsu.team.post.Category;
import com.nsu.team.post.MeetupLocation;
import com.nsu.team.post.SalePost;

import java.time.Instant;

public final class FavoriteDtos {
    private FavoriteDtos() {}

    public record CategoryResponse(String id, String name, int sortOrder) {
        static CategoryResponse from(Category category) {
            return category == null ? null : new CategoryResponse(
                    category.getId().toString(), category.getName(), category.getDisplayOrder());
        }
    }

    public record TradePlaceResponse(String id, String name, String description, int sortOrder) {
        static TradePlaceResponse from(MeetupLocation location) {
            return location == null ? null : new TradePlaceResponse(
                    location.getId().toString(), location.getName(), location.getDescription(), 0);
        }
    }

    public record PostSummary(
            String id, String title, long price, String status, String thumbnailUrl,
            CategoryResponse category, TradePlaceResponse tradePlace, PublicUserResponse seller,
            long viewCount, long favoriteCount, boolean isFavorited, Instant createdAt
    ) {
        static PostSummary from(SalePost post, long favoriteCount) {
            return new PostSummary(
                    post.getId().toString(), post.getTitle(), post.getPrice().longValueExact(),
                    post.getStatus().name(), null, CategoryResponse.from(post.getCategory()),
                    TradePlaceResponse.from(post.getMeetupLocation()), PublicUserResponse.from(post.getSeller()),
                    post.getViewCount(), favoriteCount, true, post.getCreatedAt());
        }
    }

    public record FavoriteItem(PostSummary post, Instant favoritedAt) {}
}

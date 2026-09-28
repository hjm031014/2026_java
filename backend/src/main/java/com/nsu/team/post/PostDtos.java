package com.nsu.team.post;

import com.nsu.team.domain.image.PostImage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PostDtos {
    private PostDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 10_000) String description,
            @NotNull @PositiveOrZero Long price,
            @NotNull Long categoryId,
            @NotNull Long tradePlaceId,
            @Size(max = 10) List<@NotNull Long> imageIds
    ) {
    }

    public record UpdateRequest(
            @NotNull @PositiveOrZero Long version,
            @Size(min = 1, max = 150) String title,
            @Size(min = 1, max = 10_000) String description,
            @PositiveOrZero Long price,
            Long categoryId,
            Long tradePlaceId,
            @Size(max = 10) List<@NotNull Long> imageIds
    ) {
    }

    public record StatusRequest(
            @NotNull SalePost.Status status,
            @NotNull @PositiveOrZero Long version
    ) {
    }

    public record CategoryResponse(String id, String name, int sortOrder) {
        static CategoryResponse from(Category category) {
            return new CategoryResponse(String.valueOf(category.getId()), category.getName(), category.getDisplayOrder());
        }
    }

    public record TradePlaceResponse(String id, String name, String description, int sortOrder) {
        static TradePlaceResponse from(MeetupLocation location) {
            return new TradePlaceResponse(String.valueOf(location.getId()), location.getName(),
                    location.getDescription(), location.getDisplayOrder());
        }
    }

    public record SellerResponse(String id, String nickname) {
    }

    public record ImageResponse(String id, String url, String mimeType, long size, Integer width, Integer height) {
        static ImageResponse from(PostImage image) {
            return new ImageResponse(String.valueOf(image.getId()), image.getImageUrl(),
                    image.getMimeType().getContentType(), image.getSizeBytes(), image.getWidth(), image.getHeight());
        }
    }

    public record Summary(
            String id,
            String title,
            long price,
            String status,
            String thumbnailUrl,
            CategoryResponse category,
            TradePlaceResponse tradePlace,
            SellerResponse seller,
            long viewCount,
            long favoriteCount,
            boolean isFavorited,
            Instant createdAt
    ) {
    }

    public record Detail(
            String id,
            String title,
            long price,
            String status,
            String thumbnailUrl,
            CategoryResponse category,
            TradePlaceResponse tradePlace,
            SellerResponse seller,
            long viewCount,
            long favoriteCount,
            boolean isFavorited,
            Instant createdAt,
            String description,
            List<ImageResponse> images,
            long version,
            Instant updatedAt
    ) {
    }

    public record ViewResponse(long viewCount, boolean counted) {
    }
}

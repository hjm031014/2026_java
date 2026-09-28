package com.nsu.team.post;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.common.response.PageResponse;
import com.nsu.team.common.util.CursorCodec;
import com.nsu.team.domain.image.PostImage;
import com.nsu.team.domain.image.PostImageRepository;
import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import com.nsu.team.favorite.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.nsu.team.common.exception.ErrorCode.FORBIDDEN;
import static com.nsu.team.common.exception.ErrorCode.INVALID_STATUS_TRANSITION;
import static com.nsu.team.common.exception.ErrorCode.RESOURCE_NOT_FOUND;
import static com.nsu.team.common.exception.ErrorCode.VALIDATION_ERROR;
import static com.nsu.team.common.exception.ErrorCode.VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final Duration VIEW_DEDUPLICATION_WINDOW = Duration.ofHours(24);

    private final SalePostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final MeetupLocationRepository meetupLocationRepository;
    private final PostImageRepository imageRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final PostViewEventRepository viewEventRepository;

    @Transactional
    public PostDtos.Detail create(PostDtos.CreateRequest request, Long sellerId) {
        User seller = userRepository.findById(sellerId).orElseThrow(() -> new BusinessException(RESOURCE_NOT_FOUND));
        Category category = requireCategory(request.categoryId());
        MeetupLocation location = requireLocation(request.tradePlaceId());
        SalePost post = postRepository.save(new SalePost(seller, category, location,
                request.title().trim(), request.description().trim(), BigDecimal.valueOf(request.price())));
        List<PostImage> images = requireAttachableImages(request.imageIds(), sellerId, null);
        attach(post, images);
        postRepository.flush();
        return toDetail(post, images, 0, false);
    }

    public PageResponse<PostDtos.Summary> list(String query, Long categoryId, SalePost.Status status,
                                                String sortValue, String cursor, int limit, Long viewerId) {
        if (status == SalePost.Status.DELETED) {
            throw new BusinessException(VALIDATION_ERROR);
        }
        PostSort sort = PostSort.parse(sortValue);
        PostCursor decoded = decodeCursor(cursor, sort);
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        List<SalePost> found = switch (sort) {
            case LATEST -> postRepository.findLatestPage(normalizedQuery, categoryId, status,
                    decoded == null ? null : decoded.time(), decoded == null ? null : decoded.id(),
                    PageRequest.of(0, limit + 1));
            case PRICE_ASC -> postRepository.findPriceAscPage(normalizedQuery, categoryId, status,
                    decoded == null ? null : decoded.price(), decoded == null ? null : decoded.id(),
                    PageRequest.of(0, limit + 1));
            case PRICE_DESC -> postRepository.findPriceDescPage(normalizedQuery, categoryId, status,
                    decoded == null ? null : decoded.price(), decoded == null ? null : decoded.id(),
                    PageRequest.of(0, limit + 1));
        };
        boolean hasNext = found.size() > limit;
        if (hasNext) {
            found = new ArrayList<>(found.subList(0, limit));
        }
        Map<Long, List<PostImage>> images = imagesByPost(found);
        Map<Long, Long> favoriteCounts = favoriteCounts(found);
        Set<Long> favoritedIds = favoritedIds(found, viewerId);
        List<PostDtos.Summary> items = found.stream()
                .map(post -> toSummary(post, images.getOrDefault(post.getId(), List.of()),
                        favoriteCounts.getOrDefault(post.getId(), 0L), favoritedIds.contains(post.getId())))
                .toList();
        String nextCursor = hasNext && !found.isEmpty() ? encodeCursor(found.get(found.size() - 1), sort) : null;
        return PageResponse.of(items, nextCursor, hasNext);
    }

    public PostDtos.Detail detail(Long postId, Long viewerId) {
        SalePost post = requireVisible(postId);
        List<PostImage> images = imageRepository.findAllByPostIdOrderByDisplayOrderAscIdAsc(postId);
        long favoriteCount = favoriteRepository.countByIdPostId(postId);
        boolean favorited = viewerId != null
                && favoriteRepository.existsById(new com.nsu.team.favorite.FavoriteId(viewerId, postId));
        return toDetail(post, images, favoriteCount, favorited);
    }

    @Transactional
    public PostDtos.Detail update(Long postId, PostDtos.UpdateRequest request, Long requesterId) {
        SalePost post = requireVisible(postId);
        requireOwner(post, requesterId);
        requireVersion(post, request.version());
        if (post.getStatus() == SalePost.Status.SOLD) {
            throw new BusinessException(INVALID_STATUS_TRANSITION);
        }
        if ((request.title() != null && request.title().isBlank())
                || (request.description() != null && request.description().isBlank())) {
            throw new BusinessException(VALIDATION_ERROR);
        }

        String title = request.title() == null ? post.getTitle() : request.title().trim();
        String description = request.description() == null ? post.getDescription() : request.description().trim();
        BigDecimal price = request.price() == null ? post.getPrice() : BigDecimal.valueOf(request.price());
        Category category = request.categoryId() == null ? post.getCategory() : requireCategory(request.categoryId());
        MeetupLocation location = request.tradePlaceId() == null
                ? post.getMeetupLocation() : requireLocation(request.tradePlaceId());
        List<PostImage> images = request.imageIds() == null
                ? imageRepository.findAllByPostIdOrderByDisplayOrderAscIdAsc(postId)
                : replaceImages(post, request.imageIds(), requesterId);
        post.update(title, description, price, category, location);
        postRepository.flush();
        return toDetail(post, images, favoriteRepository.countByIdPostId(postId),
                favoriteRepository.existsById(new com.nsu.team.favorite.FavoriteId(requesterId, postId)));
    }

    @Transactional
    public void delete(Long postId, long version, Long requesterId) {
        SalePost post = requireVisible(postId);
        requireOwner(post, requesterId);
        requireVersion(post, version);
        post.delete();
        postRepository.flush();
    }

    @Transactional
    public PostDtos.Detail changeStatus(Long postId, PostDtos.StatusRequest request, Long requesterId) {
        SalePost post = requireVisible(postId);
        requireOwner(post, requesterId);
        requireVersion(post, request.version());
        if (!isAllowedTransition(post.getStatus(), request.status())) {
            throw new BusinessException(INVALID_STATUS_TRANSITION);
        }
        post.changeStatus(request.status());
        postRepository.flush();
        List<PostImage> images = imageRepository.findAllByPostIdOrderByDisplayOrderAscIdAsc(postId);
        return toDetail(post, images, favoriteRepository.countByIdPostId(postId),
                favoriteRepository.existsById(new com.nsu.team.favorite.FavoriteId(requesterId, postId)));
    }

    @Transactional
    public PostDtos.ViewResponse recordView(Long postId, Long viewerId, String clientAddress) {
        SalePost post = postRepository.findByIdForUpdate(postId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new BusinessException(RESOURCE_NOT_FOUND));
        if (viewerId != null && post.getSeller().getId().equals(viewerId)) {
            return new PostDtos.ViewResponse(post.getViewCount(), false);
        }
        String viewerKey = viewerId != null ? "user:" + viewerId : "ip:" + sha256(clientAddress);
        Instant now = Instant.now();
        PostViewEvent event = viewEventRepository.findByPostIdAndViewerKey(postId, viewerKey).orElse(null);
        if (event != null && event.getViewedAt().isAfter(now.minus(VIEW_DEDUPLICATION_WINDOW))) {
            return new PostDtos.ViewResponse(post.getViewCount(), false);
        }
        if (event == null) {
            viewEventRepository.saveAndFlush(new PostViewEvent(post, viewerKey, now));
        } else {
            event.viewedAt(now);
        }
        post.incrementViewCount();
        postRepository.flush();
        return new PostDtos.ViewResponse(post.getViewCount(), true);
    }

    private SalePost requireVisible(Long postId) {
        return postRepository.findVisibleDetail(postId)
                .orElseThrow(() -> new BusinessException(RESOURCE_NOT_FOUND));
    }

    private Category requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(RESOURCE_NOT_FOUND));
    }

    private MeetupLocation requireLocation(Long locationId) {
        return meetupLocationRepository.findById(locationId)
                .filter(MeetupLocation::isActive)
                .orElseThrow(() -> new BusinessException(RESOURCE_NOT_FOUND));
    }

    private void requireOwner(SalePost post, Long requesterId) {
        if (!post.getSeller().getId().equals(requesterId)) {
            throw new BusinessException(FORBIDDEN);
        }
    }

    private void requireVersion(SalePost post, long version) {
        if (post.getVersion() != version) {
            throw new BusinessException(VERSION_CONFLICT);
        }
    }

    private boolean isAllowedTransition(SalePost.Status current, SalePost.Status target) {
        if (target == SalePost.Status.DELETED || current == SalePost.Status.SOLD || current == target) {
            return false;
        }
        return (current == SalePost.Status.SELLING && (target == SalePost.Status.RESERVED || target == SalePost.Status.SOLD))
                || (current == SalePost.Status.RESERVED && (target == SalePost.Status.SELLING || target == SalePost.Status.SOLD));
    }

    private List<PostImage> replaceImages(SalePost post, List<Long> imageIds, Long ownerId) {
        List<PostImage> replacement = requireAttachableImages(imageIds, ownerId, post.getId());
        imageRepository.findAllByPostIdOrderByDisplayOrderAscIdAsc(post.getId()).forEach(PostImage::detach);
        attach(post, replacement);
        return replacement;
    }

    private List<PostImage> requireAttachableImages(List<Long> imageIds, Long ownerId, Long currentPostId) {
        if (imageIds == null || imageIds.isEmpty()) {
            return List.of();
        }
        if (imageIds.size() > 10 || new HashSet<>(imageIds).size() != imageIds.size()) {
            throw new BusinessException(VALIDATION_ERROR);
        }
        Map<Long, PostImage> found = new HashMap<>();
        imageRepository.findAllDetailedByIdIn(imageIds).forEach(image -> found.put(image.getId(), image));
        List<PostImage> ordered = new ArrayList<>();
        for (Long imageId : imageIds) {
            PostImage image = found.get(imageId);
            if (image == null) {
                throw new BusinessException(RESOURCE_NOT_FOUND);
            }
            if (!image.getUploader().getId().equals(ownerId)) {
                throw new BusinessException(FORBIDDEN);
            }
            if (image.isAttached() && !image.getPost().getId().equals(currentPostId)) {
                throw new BusinessException(ErrorCode.IMAGE_ALREADY_ATTACHED);
            }
            ordered.add(image);
        }
        return ordered;
    }

    private void attach(SalePost post, List<PostImage> images) {
        for (int index = 0; index < images.size(); index++) {
            images.get(index).attachTo(post, index);
        }
    }

    private Map<Long, List<PostImage>> imagesByPost(List<SalePost> posts) {
        if (posts.isEmpty()) return Map.of();
        Map<Long, List<PostImage>> grouped = new LinkedHashMap<>();
        imageRepository.findAllByPostIds(posts.stream().map(SalePost::getId).toList())
                .forEach(image -> grouped.computeIfAbsent(image.getPost().getId(), ignored -> new ArrayList<>()).add(image));
        return grouped;
    }

    private Map<Long, Long> favoriteCounts(List<SalePost> posts) {
        if (posts.isEmpty()) return Map.of();
        Map<Long, Long> result = new HashMap<>();
        favoriteRepository.countByPostIds(posts.stream().map(SalePost::getId).toList())
                .forEach(row -> result.put(row.getPostId(), row.getFavoriteCount()));
        return result;
    }

    private Set<Long> favoritedIds(List<SalePost> posts, Long viewerId) {
        if (viewerId == null || posts.isEmpty()) return Set.of();
        return new HashSet<>(favoriteRepository.findFavoritedPostIds(
                viewerId, posts.stream().map(SalePost::getId).toList()));
    }

    private PostDtos.Summary toSummary(SalePost post, List<PostImage> images,
                                       long favoriteCount, boolean favorited) {
        return new PostDtos.Summary(String.valueOf(post.getId()), post.getTitle(), post.getPrice().longValueExact(),
                post.getStatus().name(), images.isEmpty() ? null : images.get(0).getImageUrl(),
                PostDtos.CategoryResponse.from(post.getCategory()),
                PostDtos.TradePlaceResponse.from(post.getMeetupLocation()),
                new PostDtos.SellerResponse(String.valueOf(post.getSeller().getId()), post.getSeller().getNickname()),
                post.getViewCount(), favoriteCount, favorited, post.getCreatedAt());
    }

    private PostDtos.Detail toDetail(SalePost post, List<PostImage> images,
                                     long favoriteCount, boolean favorited) {
        PostDtos.Summary summary = toSummary(post, images, favoriteCount, favorited);
        return new PostDtos.Detail(summary.id(), summary.title(), summary.price(), summary.status(),
                summary.thumbnailUrl(), summary.category(), summary.tradePlace(), summary.seller(),
                summary.viewCount(), summary.favoriteCount(), summary.isFavorited(), summary.createdAt(),
                post.getDescription(), images.stream().map(PostDtos.ImageResponse::from).toList(),
                post.getVersion(), post.getUpdatedAt());
    }

    private String encodeCursor(SalePost post, PostSort sort) {
        String raw = switch (sort) {
            case LATEST -> sort.value + "|" + post.getCreatedAt() + "|" + post.getId();
            case PRICE_ASC, PRICE_DESC -> sort.value + "|" + post.getPrice().toPlainString() + "|" + post.getId();
        };
        return CursorCodec.encode(raw);
    }

    private PostCursor decodeCursor(String cursor, PostSort expectedSort) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] values = CursorCodec.decode(cursor).split("\\|", -1);
            if (values.length != 3 || !values[0].equals(expectedSort.value)) throw new IllegalArgumentException();
            long id = Long.parseLong(values[2]);
            return expectedSort == PostSort.LATEST
                    ? new PostCursor(Instant.parse(values[1]), null, id)
                    : new PostCursor(null, new BigDecimal(values[1]), id);
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException business
                    && business.getErrorCode() == ErrorCode.INVALID_CURSOR) throw business;
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private enum PostSort {
        LATEST("latest"), PRICE_ASC("price_asc"), PRICE_DESC("price_desc");

        private final String value;

        PostSort(String value) {
            this.value = value;
        }

        static PostSort parse(String value) {
            String normalized = value == null || value.isBlank() ? "latest" : value.toLowerCase(Locale.ROOT);
            for (PostSort sort : values()) if (sort.value.equals(normalized)) return sort;
            throw new BusinessException(VALIDATION_ERROR, "지원하지 않는 정렬 방식입니다.");
        }
    }

    private record PostCursor(Instant time, BigDecimal price, long id) {
    }
}

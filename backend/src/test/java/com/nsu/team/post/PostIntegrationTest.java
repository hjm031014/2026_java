package com.nsu.team.post;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.domain.image.ImageMimeType;
import com.nsu.team.domain.image.ImageService;
import com.nsu.team.domain.image.PostImage;
import com.nsu.team.domain.image.PostImageRepository;
import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import com.nsu.team.domain.user.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class PostIntegrationTest {

    @Autowired PostService postService;
    @Autowired ImageService imageService;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MeetupLocationRepository locationRepository;
    @Autowired PostImageRepository imageRepository;
	@Autowired PostViewEventRepository viewEventRepository;
    @Autowired EntityManager entityManager;

    @MockitoBean Cloudinary cloudinary;

    User seller;
    User buyer;
    Category category;
    MeetupLocation location;

    @BeforeEach
    void setUp() {
        seller = userRepository.save(user("seller-post@example.com", "판매자"));
        buyer = userRepository.save(user("buyer-post@example.com", "구매자"));
        category = categoryRepository.save(new Category("테스트-도서", 1));
        location = locationRepository.save(new MeetupLocation("테스트-정문", "정문 앞", 1));
        entityManager.flush();
    }

    @Test
    void createThenReadDetailAndList() {
        PostImage image = imageRepository.save(image(seller, "https://cdn.example/one.png"));

        PostDtos.Detail created = create("스프링 책", 12000, List.of(image.getId()));
        PostDtos.Detail detail = postService.detail(Long.valueOf(created.id()), null);
        var page = postService.list("스프링", category.getId(), SalePost.Status.SELLING,
                "latest", null, 20, null);

        assertThat(detail.title()).isEqualTo("스프링 책");
        assertThat(detail.thumbnailUrl()).isEqualTo("https://cdn.example/one.png");
        assertThat(detail.images()).hasSize(1);
        assertThat(detail.isFavorited()).isFalse();
        assertThat(page.items()).extracting(PostDtos.Summary::id).containsExactly(created.id());
        assertThat(page.page().hasNext()).isFalse();
    }

    @Test
    void nonOwnerCannotUpdateOrDeleteAndVersionMismatchConflicts() {
        PostDtos.Detail created = create("노트북", 500000, List.of());
        long postId = Long.parseLong(created.id());
        PostDtos.UpdateRequest update = new PostDtos.UpdateRequest(
                created.version(), "수정", null, null, null, null, null);

        assertError(() -> postService.update(postId, update, buyer.getId()), ErrorCode.FORBIDDEN);
        assertError(() -> postService.delete(postId, created.version(), buyer.getId()), ErrorCode.FORBIDDEN);
        assertError(() -> postService.update(postId,
                new PostDtos.UpdateRequest(created.version() + 1, "수정", null, null, null, null, null),
                seller.getId()), ErrorCode.VERSION_CONFLICT);
    }

    @Test
    void soldPostCannotBeChangedOrReturnedToSelling() {
        PostDtos.Detail created = create("완료될 글", 1000, List.of());
        long postId = Long.parseLong(created.id());
        PostDtos.Detail sold = postService.changeStatus(postId,
                new PostDtos.StatusRequest(SalePost.Status.SOLD, created.version()), seller.getId());

        assertError(() -> postService.update(postId,
                new PostDtos.UpdateRequest(sold.version(), "수정 불가", null, null, null, null, null),
                seller.getId()), ErrorCode.INVALID_STATUS_TRANSITION);
        assertError(() -> postService.changeStatus(postId,
                new PostDtos.StatusRequest(SalePost.Status.SELLING, sold.version()), seller.getId()),
                ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void imageMustBelongToSellerAndCannotBeReattachedOrDeletedWhileInUse() {
        PostImage foreign = imageRepository.save(image(buyer, "https://cdn.example/foreign.png"));
        assertError(() -> create("남의 이미지", 1000, List.of(foreign.getId())), ErrorCode.FORBIDDEN);

        PostImage own = imageRepository.save(image(seller, "https://cdn.example/own.png"));
        create("첫 글", 1000, List.of(own.getId()));
        assertError(() -> create("두 번째 글", 2000, List.of(own.getId())), ErrorCode.IMAGE_ALREADY_ATTACHED);
        assertError(() -> imageService.deleteUnattached(own.getId(), seller.getId()), ErrorCode.IMAGE_IN_USE);
    }

    @Test
    void viewIsCountedOncePerViewerForTwentyFourHoursAndSellerIsExcluded() {
        PostDtos.Detail created = create("조회수", 1000, List.of());
        long postId = Long.parseLong(created.id());

        assertThat(postService.recordView(postId, seller.getId(), "127.0.0.1").counted()).isFalse();
        assertThat(postService.recordView(postId, null, "127.0.0.1").counted()).isTrue();
        assertThat(postService.recordView(postId, null, "127.0.0.1").counted()).isFalse();
        assertThat(postService.recordView(postId, buyer.getId(), "ignored").counted()).isTrue();
        assertThat(postService.recordView(postId, buyer.getId(), "ignored").counted()).isFalse();
        assertThat(postService.detail(postId, null).viewCount()).isEqualTo(2);

		var events = viewEventRepository.findAll();
		assertThat(events).hasSize(2);
		assertThat(events).allSatisfy(event -> {
			assertThat(event.getViewerKeyHash()).matches("[0-9a-f]{64}");
			assertThat(event.getFirstViewedAt()).isNotNull();
			assertThat(event.getLastCountedAt()).isNotNull();
			assertThat(event.getViewerKey()).doesNotContain("127.0.0.1");
		});
		assertThat(events).extracting(PostViewEvent::getViewerUserId)
				.containsExactlyInAnyOrder(null, buyer.getId());
    }

    @Test
    void cursorPaginationHasNextAndRejectsInvalidCursor() {
        create("첫 글", 1000, List.of());
        create("둘째 글", 2000, List.of());
        create("셋째 글", 3000, List.of());

        var first = postService.list(null, null, null, "latest", null, 2, null);
        var second = postService.list(null, null, null, "latest", first.page().nextCursor(), 2, null);

        assertThat(first.items()).hasSize(2);
        assertThat(first.page().hasNext()).isTrue();
        assertThat(first.page().nextCursor()).isNotBlank();
        assertThat(second.items()).hasSize(1);
        assertThat(second.page().hasNext()).isFalse();
        assertError(() -> postService.list(null, null, null, "latest", "not-a-cursor", 20, null),
                ErrorCode.INVALID_CURSOR);
    }

    @Test
    void listSupportsEmptyQueryOptionalFiltersAndEverySort() {
        PostDtos.Detail first = create("첫 글", 3000, List.of());
        PostDtos.Detail second = create("둘째 글", 1000, List.of());
        PostDtos.Detail third = create("셋째 글", 2000, List.of());

        var unfiltered = postService.list("", null, null, "latest", null, 20, null);
        var filtered = postService.list("둘째", category.getId(), SalePost.Status.SELLING,
                "latest", null, 20, null);
        var priceAsc = postService.list(null, null, null, "price_asc", null, 20, null);
        var priceDesc = postService.list(null, null, null, "price_desc", null, 20, null);

        assertThat(unfiltered.items()).extracting(PostDtos.Summary::id)
                .containsExactlyInAnyOrder(first.id(), second.id(), third.id());
        assertThat(filtered.items()).extracting(PostDtos.Summary::id).containsExactly(second.id());
        assertThat(priceAsc.items()).extracting(PostDtos.Summary::price).containsExactly(1000L, 2000L, 3000L);
        assertThat(priceDesc.items()).extracting(PostDtos.Summary::price).containsExactly(3000L, 2000L, 1000L);
    }

    @Test
    void imageRejectsOversizedAndSpoofedFilesBeforeCloudinaryCall() {
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "fake.png", "image/png", "not png".getBytes());
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.png", "image/png", new byte[10 * 1024 * 1024 + 1]);

        assertError(() -> imageService.upload(spoofed, seller.getId()), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertError(() -> imageService.upload(oversized, seller.getId()), ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void imageUploadStoresCloudinaryMetadataAndDeleteRemovesRemoteAsset() throws Exception {
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/sample.png",
                "public_id", "campus-marketplace/sample",
                "bytes", 8,
                "width", 1,
                "height", 1));

        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile file = new MockMultipartFile("file", "sample.png", "image/png", pngHeader);

        var uploaded = imageService.upload(file, seller.getId());
        PostImage stored = imageRepository.findById(Long.valueOf(uploaded.id())).orElseThrow();

        assertThat(uploaded.mimeType()).isEqualTo("image/png");
        assertThat(stored.getCloudinaryPublicId()).isEqualTo("campus-marketplace/sample");

        imageService.deleteUnattached(stored.getId(), seller.getId());

        verify(uploader).destroy(eq("campus-marketplace/sample"), any(Map.class));
        assertThat(imageRepository.findById(stored.getId())).isEmpty();
    }

    private PostDtos.Detail create(String title, long price, List<Long> imageIds) {
        return postService.create(new PostDtos.CreateRequest(
                title, "설명", price, category.getId(), location.getId(), imageIds), seller.getId());
    }

    private PostImage image(User uploader, String url) {
        return PostImage.builder()
                .uploader(uploader)
                .imageUrl(url)
                .mimeType(ImageMimeType.PNG)
                .sizeBytes(100L)
                .width(10)
                .height(10)
                .build();
    }

    private User user(String email, String nickname) {
        return User.builder()
                .email(email)
                .passwordHash("hash")
                .nickname(nickname)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}

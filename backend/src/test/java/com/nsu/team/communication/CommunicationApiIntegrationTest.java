package com.nsu.team.communication;

import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import com.nsu.team.domain.user.UserStatus;
import com.nsu.team.domain.image.ImageMimeType;
import com.nsu.team.domain.image.PostImage;
import com.nsu.team.domain.image.PostImageRepository;
import com.nsu.team.post.Category;
import com.nsu.team.post.CategoryRepository;
import com.nsu.team.post.MeetupLocation;
import com.nsu.team.post.MeetupLocationRepository;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import com.nsu.team.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommunicationApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SalePostRepository salePostRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private MeetupLocationRepository meetupLocationRepository;

	@Autowired
	private PostImageRepository postImageRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private User seller;
	private User buyer;
	private SalePost post;
	private String buyerAccessToken;

	@BeforeEach
	void setUp() {
		seller = userRepository.save(newUser("api-seller@example.com", "판매자"));
		buyer = userRepository.save(newUser("api-buyer@example.com", "구매자"));
		Category category = categoryRepository.save(new Category("통합 카테고리", 1));
		MeetupLocation location = meetupLocationRepository.save(
				new MeetupLocation("통합 장소", "통합 테스트 장소", 1));
		post = salePostRepository.save(new SalePost(
				seller, category, location, "HTTP 통합 테스트", "상품 설명", BigDecimal.valueOf(10000)));
		PostImage image = PostImage.builder()
				.uploader(seller)
				.imageUrl("https://cdn.example/integration.png")
				.mimeType(ImageMimeType.PNG)
				.sizeBytes(100L)
				.width(10)
				.height(10)
				.build();
		image.attachTo(post, 0);
		postImageRepository.save(image);
		buyerAccessToken = jwtTokenProvider.generateAccessToken(
				buyer.getId(), buyer.getEmail(), buyer.getNickname());
	}

	@Test
	void jwtPrincipalFlowsThroughCommentFavoriteAndChatApis() throws Exception {
		mockMvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
					.header("Authorization", bearer(buyerAccessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"content\":\"구매하고 싶습니다\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.author.id").value(buyer.getId().toString()))
				.andExpect(jsonPath("$.data.content").value("구매하고 싶습니다"));

		mockMvc.perform(put("/api/v1/posts/{postId}/favorite", post.getId())
					.header("Authorization", bearer(buyerAccessToken)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/users/me/favorites")
					.header("Authorization", bearer(buyerAccessToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].post.id").value(post.getId().toString()))
				.andExpect(jsonPath("$.data.items[0].post.thumbnailUrl")
						.value("https://cdn.example/integration.png"))
				.andExpect(jsonPath("$.data.items[0].post.category.name").value("통합 카테고리"))
				.andExpect(jsonPath("$.data.items[0].post.isFavorited").value(true));

		mockMvc.perform(post("/api/v1/chat/rooms")
					.header("Authorization", bearer(buyerAccessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"postId\":" + post.getId() + "}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.otherUser.id").value(seller.getId().toString()));
	}

	@Test
	void referenceDataApisArePublicAndContractAligned() throws Exception {
		mockMvc.perform(get("/api/v1/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].name").value("통합 카테고리"))
				.andExpect(jsonPath("$.data.items[0].sortOrder").value(1));

		mockMvc.perform(get("/api/v1/trade-places"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].name").value("통합 장소"))
				.andExpect(jsonPath("$.data.items[0].description").value("통합 테스트 장소"));
	}

	@Test
	void chatRoomCreationReturnsCreatedFirstThenOkOnReuse() throws Exception {
		mockMvc.perform(post("/api/v1/chat/rooms")
						.header("Authorization", bearer(buyerAccessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"postId\":" + post.getId() + "}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/chat/rooms")
						.header("Authorization", bearer(buyerAccessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"postId\":" + post.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.otherUser.id").value(seller.getId().toString()));
	}

	@Test
	void favoriteRemoveIsIdempotentAndReturnsNoContentWhenNotFavorited() throws Exception {
		mockMvc.perform(delete("/api/v1/posts/{postId}/favorite", post.getId())
						.header("Authorization", bearer(buyerAccessToken)))
				.andExpect(status().isNoContent());

		mockMvc.perform(put("/api/v1/posts/{postId}/favorite", post.getId())
						.header("Authorization", bearer(buyerAccessToken)))
				.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/posts/{postId}/favorite", post.getId())
						.header("Authorization", bearer(buyerAccessToken)))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/posts/{postId}/favorite", post.getId())
						.header("Authorization", bearer(buyerAccessToken)))
				.andExpect(status().isNoContent());
	}

	@Test
	void protectedCommunicationApiRejectsMissingAccessToken() throws Exception {
		mockMvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"content\":\"인증 없음\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private User newUser(String email, String nickname) {
		return User.builder()
				.email(email)
				.passwordHash("hash")
				.nickname(nickname)
				.status(UserStatus.ACTIVE)
				.build();
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}
}

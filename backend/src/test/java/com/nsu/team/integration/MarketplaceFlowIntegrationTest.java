package com.nsu.team.integration;

import com.nsu.team.post.Category;
import com.nsu.team.post.CategoryRepository;
import com.nsu.team.post.MeetupLocation;
import com.nsu.team.post.MeetupLocationRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarketplaceFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private MeetupLocationRepository meetupLocationRepository;

	@Test
	void signupLoginPostCommentFavoriteAndChatFlow() throws Exception {
		Category category = categoryRepository.save(new Category("전체 통합 카테고리", 1));
		MeetupLocation tradePlace = meetupLocationRepository.save(
				new MeetupLocation("전체 통합 장소", "학생회관 앞", 1));

		MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		String csrfToken = data(csrfResult).get("csrfToken").asString();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

		String sellerToken = signupAndLogin(
				"flow-seller@example.com", "판매자통합", csrfToken, csrfCookie);
		String buyerToken = signupAndLogin(
				"flow-buyer@example.com", "구매자통합", csrfToken, csrfCookie);

		String createPostBody = objectMapper.writeValueAsString(new CreatePostPayload(
				"통합 판매글", "A, B, C 전체 통합 상품", 15000L,
				category.getId(), tradePlace.getId()));
		MvcResult postResult = mockMvc.perform(post("/api/v1/posts")
					.header("Authorization", bearer(sellerToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(createPostBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.title").value("통합 판매글"))
				.andExpect(jsonPath("$.data.category.id").value(category.getId().toString()))
				.andReturn();
		String postId = data(postResult).get("id").asString();

		mockMvc.perform(get("/api/v1/posts/{postId}", postId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isFavorited").value(false));

		mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
					.header("Authorization", bearer(buyerToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"content\":\"구매 희망합니다\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.author.nickname").value("구매자통합"));

		mockMvc.perform(put("/api/v1/posts/{postId}/favorite", postId)
					.header("Authorization", bearer(buyerToken)))
				.andExpect(status().isNoContent());

		MvcResult roomResult = mockMvc.perform(post("/api/v1/chat/rooms")
					.header("Authorization", bearer(buyerToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"postId\":" + postId + "}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.otherUser.nickname").value("판매자통합"))
				.andReturn();
		String roomId = data(roomResult).get("id").asString();

		String clientMessageId = UUID.randomUUID().toString();
		mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", roomId)
					.header("Authorization", bearer(buyerToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"clientMessageId\":\"" + clientMessageId
							+ "\",\"content\":\"채팅 메시지입니다\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.sequence").value(1));

		mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", roomId)
					.header("Authorization", bearer(sellerToken))
					.param("afterSequence", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].content").value("채팅 메시지입니다"));

		mockMvc.perform(get("/api/v1/users/me/favorites")
					.header("Authorization", bearer(buyerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].post.id").value(postId))
				.andExpect(jsonPath("$.data.items[0].post.favoriteCount").value(1));
	}

	private String signupAndLogin(
			String email, String nickname, String csrfToken, Cookie csrfCookie) throws Exception {
		String signupBody = objectMapper.writeValueAsString(
				new SignupPayload(email, "password1234", nickname));
		mockMvc.perform(post("/api/v1/auth/signup")
					.header("X-CSRF-Token", csrfToken)
					.cookie(csrfCookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(signupBody))
				.andExpect(status().isCreated());

		String loginBody = objectMapper.writeValueAsString(new LoginPayload(email, "password1234"));
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
					.header("X-CSRF-Token", csrfToken)
					.cookie(csrfCookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginBody))
				.andExpect(status().isOk())
				.andReturn();
		return data(loginResult).get("accessToken").asString();
	}

	private JsonNode data(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record SignupPayload(String email, String password, String nickname) {
	}

	private record LoginPayload(String email, String password) {
	}

	private record CreatePostPayload(
			String title,
			String description,
			Long price,
			Long categoryId,
			Long tradePlaceId) {
	}
}

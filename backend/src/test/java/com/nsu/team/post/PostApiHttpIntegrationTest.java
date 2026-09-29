package com.nsu.team.post;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트 B(판매글·이미지) 9개 엔드포인트의 HTTP 계약을 MockMvc로 검증합니다.
 * PostIntegrationTest(서비스 레이어)·MarketplaceFlowIntegrationTest(A/B/C 전체 스모크)와
 * 겹치지 않게, 인증·권한·버전 충돌·이미지 소유권처럼 HTTP 레이어에서만 드러나는 계약을 다룹니다.
 * Cloudinary 호출은 전부 mock 처리합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostApiHttpIntegrationTest {

	private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private CategoryRepository categoryRepository;
	@Autowired private MeetupLocationRepository locationRepository;

	@MockitoBean private Cloudinary cloudinary;

	private Category category;
	private MeetupLocation location;

	private Category category() {
		if (category == null) {
			category = categoryRepository.save(new Category("HTTP-카테고리", 1));
		}
		return category;
	}

	private MeetupLocation location() {
		if (location == null) {
			location = locationRepository.save(new MeetupLocation("HTTP-장소", "설명", 1));
		}
		return location;
	}

	@Test
	void createPostSucceedsForMemberAndFailsWithoutAuthentication() throws Exception {
		String sellerToken = signupAndLogin("http-seller1@example.com", "http판매자1");

		mockMvc.perform(post("/api/v1/posts")
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createPostJson("첫 판매글", 10000)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.title").value("첫 판매글"))
				.andExpect(jsonPath("$.data.status").value("SELLING"))
				.andExpect(jsonPath("$.data.version").value(0));

		mockMvc.perform(post("/api/v1/posts")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createPostJson("비로그인 글", 5000)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void listSupportsSearchCategoryStatusSortAndCursorPagination() throws Exception {
		String sellerToken = signupAndLogin("http-seller2@example.com", "http판매자2");
		String cheapId = createPostAndGetId(sellerToken, "저렴이 노트북", 1000);
		String midId = createPostAndGetId(sellerToken, "중간 노트북", 2000);
		String expensiveId = createPostAndGetId(sellerToken, "비싼 키보드", 3000);

		mockMvc.perform(get("/api/v1/posts").param("q", "노트북").param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2));

		mockMvc.perform(get("/api/v1/posts")
						.param("categoryId", category().getId().toString())
						.param("status", "SELLING")
						.param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3));

		mockMvc.perform(get("/api/v1/posts").param("sort", "price_asc").param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(cheapId))
				.andExpect(jsonPath("$.data.items[2].id").value(expensiveId));

		mockMvc.perform(get("/api/v1/posts").param("sort", "price_desc").param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(expensiveId));

		MvcResult firstPage = mockMvc.perform(get("/api/v1/posts")
						.param("sort", "price_asc").param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.page.hasNext").value(true))
				.andReturn();
		String cursor = data(firstPage).path("page").path("nextCursor").asString();
		assertThat(cursor).isNotBlank();

		mockMvc.perform(get("/api/v1/posts")
						.param("sort", "price_asc").param("cursor", cursor).param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(expensiveId))
				.andExpect(jsonPath("$.data.page.hasNext").value(false));
	}

	@Test
	void detailReturnsFullPostBody() throws Exception {
		String sellerToken = signupAndLogin("http-seller3@example.com", "http판매자3");
		String postId = createPostAndGetId(sellerToken, "상세조회 대상", 7000);

		mockMvc.perform(get("/api/v1/posts/{postId}", postId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(postId))
				.andExpect(jsonPath("$.data.description").value("설명"))
				.andExpect(jsonPath("$.data.images").isArray())
				.andExpect(jsonPath("$.data.version").value(0));
	}

	@Test
	void ownerCanUpdateDeleteAndChangeStatusButOthersCannot() throws Exception {
		String sellerToken = signupAndLogin("http-seller4@example.com", "http판매자4");
		String buyerToken = signupAndLogin("http-buyer4@example.com", "http구매자4");
		String postId = createPostAndGetId(sellerToken, "수정 대상", 4000);

		// 다른 사용자는 수정/상태변경/삭제 모두 403
		mockMvc.perform(patch("/api/v1/posts/{postId}", postId)
						.header("Authorization", bearer(buyerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"version\":0,\"title\":\"해킹시도\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		mockMvc.perform(patch("/api/v1/posts/{postId}/status", postId)
						.header("Authorization", bearer(buyerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"SOLD\",\"version\":0}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
						.header("Authorization", bearer(buyerToken))
						.param("version", "0"))
				.andExpect(status().isForbidden());

		// 잘못된 version은 409
		mockMvc.perform(patch("/api/v1/posts/{postId}", postId)
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"version\":99,\"title\":\"버전 불일치\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));

		// 작성자는 수정 성공
		MvcResult updated = mockMvc.perform(patch("/api/v1/posts/{postId}", postId)
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"version\":0,\"title\":\"수정된 제목\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("수정된 제목"))
				.andReturn();
		long updatedVersion = data(updated).path("version").asLong();

		// 작성자는 상태 변경 성공
		mockMvc.perform(patch("/api/v1/posts/{postId}/status", postId)
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"RESERVED\",\"version\":" + updatedVersion + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("RESERVED"));

		// 작성자는 삭제 성공 후 목록/상세에서 제외
		mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
						.header("Authorization", bearer(sellerToken))
						.param("version", String.valueOf(updatedVersion + 1)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/{postId}", postId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void soldPostRejectsFurtherUpdateAndStatusRollback() throws Exception {
		String sellerToken = signupAndLogin("http-seller5@example.com", "http판매자5");
		String postId = createPostAndGetId(sellerToken, "판매완료 대상", 3000);

		mockMvc.perform(patch("/api/v1/posts/{postId}/status", postId)
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"SOLD\",\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SOLD"));

		mockMvc.perform(patch("/api/v1/posts/{postId}", postId)
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"version\":1,\"title\":\"수정 시도\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));

		mockMvc.perform(patch("/api/v1/posts/{postId}/status", postId)
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"SELLING\",\"version\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
	}

	@Test
	void viewEventDeduplicatesByMemberAndGuestAndExcludesAuthor() throws Exception {
		String sellerToken = signupAndLogin("http-seller6@example.com", "http판매자6");
		String buyerToken = signupAndLogin("http-buyer6@example.com", "http구매자6");
		String postId = createPostAndGetId(sellerToken, "조회수 대상", 1500);

		// API.md: 조회 이벤트는 CSRF 헤더+쿠키가 필요
		MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
		String csrfToken = data(csrfResult).path("csrfToken").asString();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

		mockMvc.perform(post("/api/v1/posts/{postId}/views", postId)
						.header("Authorization", bearer(sellerToken))
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.counted").value(false));

		mockMvc.perform(post("/api/v1/posts/{postId}/views", postId)
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.counted").value(true))
				.andExpect(jsonPath("$.data.viewCount").value(1));

		mockMvc.perform(post("/api/v1/posts/{postId}/views", postId)
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.counted").value(false))
				.andExpect(jsonPath("$.data.viewCount").value(1));

		mockMvc.perform(post("/api/v1/posts/{postId}/views", postId)
						.header("Authorization", bearer(buyerToken))
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.counted").value(true))
				.andExpect(jsonPath("$.data.viewCount").value(2));

		mockMvc.perform(post("/api/v1/posts/{postId}/views", postId)
						.header("Authorization", bearer(buyerToken))
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.counted").value(false))
				.andExpect(jsonPath("$.data.viewCount").value(2));
	}

	@Test
	void imageOwnershipAndDuplicateAttachmentAreEnforcedOverHttp() throws Exception {
		String sellerToken = signupAndLogin("http-seller7@example.com", "http판매자7");
		String buyerToken = signupAndLogin("http-buyer7@example.com", "http구매자7");
		mockCloudinaryUpload("campus-marketplace/foreign", "https://res.cloudinary.com/demo/foreign.png");
		String foreignImageId = uploadImageAndGetId(buyerToken);

		mockMvc.perform(post("/api/v1/posts")
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createPostJsonWithImages("남의 이미지", 1000, foreignImageId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		mockCloudinaryUpload("campus-marketplace/own", "https://res.cloudinary.com/demo/own.png");
		String ownImageId = uploadImageAndGetId(sellerToken);

		mockMvc.perform(post("/api/v1/posts")
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createPostJsonWithImages("내 이미지 첫 글", 1000, ownImageId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.images.length()").value(1));

		mockMvc.perform(post("/api/v1/posts")
						.header("Authorization", bearer(sellerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createPostJsonWithImages("같은 이미지 재사용", 1000, ownImageId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("IMAGE_ALREADY_ATTACHED"));

		mockMvc.perform(delete("/api/v1/images/{imageId}", ownImageId)
						.header("Authorization", bearer(sellerToken)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("IMAGE_IN_USE"));
	}

	@Test
	void imageUploadRejectsWrongFormatOversizeAndDeletesWhenUnattached() throws Exception {
		String sellerToken = signupAndLogin("http-seller8@example.com", "http판매자8");

		MockMultipartFile spoofed = new MockMultipartFile(
				"file", "fake.png", "image/png", "this is not a real png".getBytes());
		mockMvc.perform(multipart("/api/v1/images").file(spoofed)
						.header("Authorization", bearer(sellerToken)))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));

		MockMultipartFile oversized = new MockMultipartFile(
				"file", "large.png", "image/png", new byte[11 * 1024 * 1024]);
		mockMvc.perform(multipart("/api/v1/images").file(oversized)
						.header("Authorization", bearer(sellerToken)))
				.andExpect(status().is(413))
				.andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));

		mockCloudinaryUpload("campus-marketplace/unattached", "https://res.cloudinary.com/demo/unattached.png");
		String imageId = uploadImageAndGetId(sellerToken);

		mockMvc.perform(delete("/api/v1/images/{imageId}", imageId)
						.header("Authorization", bearer(sellerToken)))
				.andExpect(status().isNoContent());
	}

	private void mockCloudinaryUpload(String publicId, String secureUrl) throws Exception {
		Uploader uploader = org.mockito.Mockito.mock(Uploader.class);
		when(cloudinary.uploader()).thenReturn(uploader);
		when(uploader.upload(any(byte[].class), org.mockito.ArgumentMatchers.any(Map.class))).thenReturn(Map.of(
				"secure_url", secureUrl,
				"public_id", publicId,
				"bytes", 8,
				"width", 1,
				"height", 1));
	}

	private String uploadImageAndGetId(String token) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "sample.png", "image/png", PNG_HEADER);
		MvcResult result = mockMvc.perform(multipart("/api/v1/images").file(file)
						.header("Authorization", bearer(token)))
				.andExpect(status().isCreated())
				.andReturn();
		return data(result).path("id").asString();
	}

	private String createPostAndGetId(String token, String title, long price) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/posts")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createPostJson(title, price)))
				.andExpect(status().isCreated())
				.andReturn();
		return data(result).path("id").asString();
	}

	private String createPostJson(String title, long price) {
		return "{\"title\":\"" + title + "\",\"description\":\"설명\",\"price\":" + price
				+ ",\"categoryId\":" + category().getId() + ",\"tradePlaceId\":" + location().getId() + "}";
	}

	private String createPostJsonWithImages(String title, long price, String imageId) {
		return "{\"title\":\"" + title + "\",\"description\":\"설명\",\"price\":" + price
				+ ",\"categoryId\":" + category().getId() + ",\"tradePlaceId\":" + location().getId()
				+ ",\"imageIds\":[" + imageId + "]}";
	}

	private String signupAndLogin(String email, String nickname) throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
		String csrfToken = data(csrfResult).path("csrfToken").asString();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

		String signupBody = objectMapper.writeValueAsString(new SignupPayload(email, "password1234", nickname));
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
		return data(loginResult).path("accessToken").asString();
	}

	private JsonNode data(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record SignupPayload(String email, String password, String nickname) {
	}

	private record LoginPayload(String email, String password) {
	}
}

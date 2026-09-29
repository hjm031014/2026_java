package com.nsu.team.post;

import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import com.nsu.team.domain.user.UserStatus;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BACKEND_TASKS.md 에 기록된 "GET /api/v1/posts 가 PostgreSQL에서 500을 반환하던 문제"를
 * H2가 아닌 실제 PostgreSQL(임베디드)에서 재검증합니다. H2는 파라미터 타입 추론이 PostgreSQL보다
 * 관대해서 null 바인딩 회귀를 못 잡아낼 수 있으므로 별도 프로필로 분리했습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
@Transactional
class PostListPostgresCompatibilityTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private PostService postService;
	@Autowired private UserRepository userRepository;
	@Autowired private CategoryRepository categoryRepository;
	@Autowired private MeetupLocationRepository locationRepository;
	@Autowired private EntityManager entityManager;

	Category category;
	MeetupLocation location;

	@BeforeEach
	void setUp() {
		User seller = userRepository.save(User.builder()
				.email("pg-seller@example.com")
				.passwordHash("hash")
				.nickname("PG판매자")
				.status(UserStatus.ACTIVE)
				.build());
		category = categoryRepository.save(new Category("PG-도서", 1));
		location = locationRepository.save(new MeetupLocation("PG-정문", "정문 앞", 1));
		entityManager.flush();

		for (String title : List.of("스프링 입문", "자바 완전정복", "알고리즘 노트")) {
			postService.create(new PostDtos.CreateRequest(
					title, "설명", 1000L, category.getId(), location.getId(), null), seller.getId());
		}
	}

	@Test
	void listWithNoParamsReturnsOkOnRealPostgres() throws Exception {
		mockMvc.perform(get("/api/v1/posts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray());
	}

	@Test
	void listWithLimitOnlyReturnsOkOnRealPostgres() throws Exception {
		mockMvc.perform(get("/api/v1/posts").param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3));
	}

	@Test
	void listWithEmptyQueryAndLimitReturnsOkOnRealPostgres() throws Exception {
		mockMvc.perform(get("/api/v1/posts").param("q", "").param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3));
	}

	@Test
	void listWithCategoryAndStatusFilterReturnsOkOnRealPostgres() throws Exception {
		mockMvc.perform(get("/api/v1/posts")
						.param("categoryId", category.getId().toString())
						.param("status", "SELLING")
						.param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(3));
	}

	@Test
	void listSortedByPriceWithCursorPaginationReturnsOkOnRealPostgres() throws Exception {
		MvcResult firstPage = mockMvc.perform(get("/api/v1/posts")
						.param("sort", "price_asc")
						.param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.page.hasNext").value(true))
				.andReturn();
		String cursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
				.path("data").path("page").path("nextCursor").asString();

		mockMvc.perform(get("/api/v1/posts")
						.param("sort", "price_asc")
						.param("cursor", cursor)
						.param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.page.hasNext").value(false));
	}

	@Test
	void detailReturnsOkOnRealPostgres() throws Exception {
		var summary = postService.list(null, null, null, "latest", null, 1, null).items().get(0);

		mockMvc.perform(get("/api/v1/posts/{postId}", summary.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value(summary.title()));
	}
}

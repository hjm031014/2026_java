package com.nsu.team.domain.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트 A(회원가입/로그인/토큰갱신/로그아웃/내 정보) 전체 흐름을 실제 HTTP 요청으로 검증합니다.
 * CSRF 더블 서브밋 쿠키, JWT 액세스 토큰, 리프레시 토큰 로테이션이 모두 API.md 규칙대로 동작하는지 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void signupLoginRefreshLogoutFlow() throws Exception {
		// 1. CSRF 토큰 발급
		MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		String csrfToken = readData(csrfResult).get("csrfToken").asString();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrfCookie).isNotNull();

		// 2. 회원가입 (CSRF 헤더+쿠키 필요)
		String signupBody = objectMapper.writeValueAsString(new SignupPayload(
				"student@example.com", "password1234", "학생"));
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie)
						.content(signupBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.email").value("student@example.com"))
				.andExpect(jsonPath("$.data.nickname").value("학생"));

		// 3. 중복 가입은 EMAIL_ALREADY_EXISTS(409)
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie)
						.content(signupBody))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));

		// 4. 로그인
		String loginBody = objectMapper.writeValueAsString(new LoginPayload("student@example.com", "password1234"));
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie)
						.content(loginBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").exists())
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andReturn();

		String accessToken = readData(loginResult).get("accessToken").asString();
		Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
		assertThat(refreshCookie).isNotNull();

		// 5. 잘못된 비밀번호 로그인은 INVALID_CREDENTIALS(401)
		String wrongLoginBody = objectMapper.writeValueAsString(new LoginPayload("student@example.com", "wrong-password"));
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie)
						.content(wrongLoginBody))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

		// 6. 액세스 토큰으로 내 정보 조회
		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("student@example.com"));

		// 7. 토큰 없이 내 정보 조회는 UNAUTHENTICATED(401)
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		// 8. 리프레시 토큰으로 액세스 토큰 갱신 (쿠키 로테이션 확인)
		MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie, refreshCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").exists())
				.andReturn();
		Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie("refreshToken");
		assertThat(rotatedRefreshCookie).isNotNull();
		assertThat(rotatedRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

		// 9. 이미 교체되어 무효화된 예전 리프레시 토큰 재사용은 REFRESH_TOKEN_REUSED(401)
		mockMvc.perform(post("/api/v1/auth/refresh")
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie, refreshCookie))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));

		// 10. 로그아웃
		mockMvc.perform(post("/api/v1/auth/logout")
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie, rotatedRefreshCookie))
				.andExpect(status().isNoContent());

		// 11. 로그아웃된 세션의 리프레시 토큰 재사용도 REFRESH_TOKEN_REUSED(401)
		mockMvc.perform(post("/api/v1/auth/refresh")
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie, rotatedRefreshCookie))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));
	}

	@Test
	void duplicateNicknameIsRejectedEvenWithDifferentEmail() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
		String csrfToken = readData(csrfResult).get("csrfToken").asString();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

		String firstSignup = objectMapper.writeValueAsString(new SignupPayload(
				"nickname-owner@example.com", "password1234", "중복닉네임"));
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie)
						.content(firstSignup))
				.andExpect(status().isCreated());

		String secondSignup = objectMapper.writeValueAsString(new SignupPayload(
				"another-email@example.com", "password1234", "중복닉네임"));
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-CSRF-Token", csrfToken)
						.cookie(csrfCookie)
						.content(secondSignup))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("NICKNAME_ALREADY_EXISTS"));
	}

	@Test
	void csrfTokenMismatchIsRejected() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

		String signupBody = objectMapper.writeValueAsString(new SignupPayload(
				"nocsrf@example.com", "password1234", "닉네임"));

		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-CSRF-Token", "wrong-token")
						.cookie(csrfCookie)
						.content(signupBody))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
	}

	@Test
	void imageDeleteWithoutTokenRequiresAuthentication() throws Exception {
		mockMvc.perform(delete("/api/v1/images/1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private JsonNode readData(MvcResult result) throws Exception {
		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		return root.get("data");
	}

	private record SignupPayload(String email, String password, String nickname) {
	}

	private record LoginPayload(String email, String password) {
	}
}

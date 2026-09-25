package com.campusmarket.domain.auth;

import com.campusmarket.common.response.ApiResponse;
import com.campusmarket.domain.auth.dto.CsrfTokenResponse;
import com.campusmarket.domain.auth.dto.LoginRequest;
import com.campusmarket.domain.auth.dto.LoginResponse;
import com.campusmarket.domain.auth.dto.SignupRequest;
import com.campusmarket.domain.auth.dto.TokenRefreshResponse;
import com.campusmarket.domain.user.dto.MyUserResponse;
import com.campusmarket.security.csrf.CsrfTokenIssuer;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

	private final AuthService authService;
	private final CsrfTokenIssuer csrfTokenIssuer;

	@GetMapping("/csrf")
	public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(HttpServletResponse response) {
		String token = csrfTokenIssuer.issue(response);
		return ResponseEntity.ok(ApiResponse.of(new CsrfTokenResponse(token)));
	}

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<MyUserResponse>> signup(@Valid @RequestBody SignupRequest request) {
		MyUserResponse response = authService.signup(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
			HttpServletResponse response) {
		LoginResponse loginResponse = authService.login(request, response);
		return ResponseEntity.ok(ApiResponse.of(loginResponse));
	}

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
			@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
			HttpServletResponse response) {
		TokenRefreshResponse tokenRefreshResponse = authService.refresh(refreshToken, response);
		return ResponseEntity.ok(ApiResponse.of(tokenRefreshResponse));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
			HttpServletResponse response) {
		authService.logout(refreshToken, response);
		return ResponseEntity.noContent().build();
	}
}

package com.nsu.team.domain.user;

import com.nsu.team.common.response.ApiResponse;
import com.nsu.team.domain.user.dto.MyUserResponse;
import com.nsu.team.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<MyUserResponse>> getMyInfo(@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(ApiResponse.of(userService.getMyInfo(principal.userId())));
	}
}

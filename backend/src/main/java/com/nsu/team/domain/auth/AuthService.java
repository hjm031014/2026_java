package com.nsu.team.domain.auth;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.domain.auth.dto.LoginRequest;
import com.nsu.team.domain.auth.dto.LoginResponse;
import com.nsu.team.domain.auth.dto.SignupRequest;
import com.nsu.team.domain.auth.dto.TokenRefreshResponse;
import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import com.nsu.team.domain.user.UserStatus;
import com.nsu.team.domain.user.dto.MyUserResponse;
import com.nsu.team.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenService refreshTokenService;

	@Transactional
	public MyUserResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		if (userRepository.existsByNickname(request.nickname())) {
			throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
		}

		User user = User.builder()
				.email(request.email())
				.passwordHash(passwordEncoder.encode(request.password()))
				.nickname(request.nickname())
				.status(UserStatus.ACTIVE)
				.build();
		userRepository.save(user);

		return MyUserResponse.from(user);
	}

	@Transactional
	public LoginResponse login(LoginRequest request, HttpServletResponse response) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getNickname());
		refreshTokenService.issueNewSession(user, response);

		return new LoginResponse(
				MyUserResponse.from(user),
				accessToken,
				"Bearer",
				jwtTokenProvider.getAccessTokenValiditySeconds()
		);
	}

	@Transactional
	public TokenRefreshResponse refresh(String refreshTokenCookieValue, HttpServletResponse response) {
		if (refreshTokenCookieValue == null || refreshTokenCookieValue.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		RefreshTokenSession session = refreshTokenService.validateAndRotate(refreshTokenCookieValue, response);
		User user = session.getUser();
		String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getNickname());

		return new TokenRefreshResponse(accessToken, "Bearer", jwtTokenProvider.getAccessTokenValiditySeconds());
	}

	@Transactional
	public void logout(String refreshTokenCookieValue, HttpServletResponse response) {
		refreshTokenService.revoke(refreshTokenCookieValue);
		refreshTokenService.expireCookie(response);
	}
}

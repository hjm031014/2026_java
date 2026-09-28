package com.nsu.team.domain.user;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.domain.user.dto.MyUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;

	public MyUserResponse getMyInfo(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		return MyUserResponse.from(user);
	}
}

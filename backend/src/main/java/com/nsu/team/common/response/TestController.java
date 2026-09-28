package com.nsu.team.common.response;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서버 기동 여부 확인용. */
@RestController
public class TestController {

	@GetMapping({"/test", "/api/v1/test"})
	public ApiResponse<Map<String, Object>> test() {
		return ApiResponse.of(Map.of("status", "ok", "time", Instant.now().toString()));
	}
}

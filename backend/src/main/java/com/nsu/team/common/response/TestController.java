package com.nsu.team.common.response;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서버 기동 여부 확인용. */
@RestController
public class TestController {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@GetMapping({"/test", "/api/v1/test"})
	public ApiResponse<Map<String, Object>> test() {
		LocalDateTime now = LocalDateTime.now(ZONE);
		return ApiResponse.of(Map.of(
				"status", "ok",
				"date", now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
				"time", now.toLocalTime().withNano(0).format(DateTimeFormatter.ISO_LOCAL_TIME),
				"timezone", ZONE.getId()));
	}
}

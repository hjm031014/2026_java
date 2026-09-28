package com.nsu.team.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cloudinary 연결 정보(CLOUDINARY_URL)가 아직 없어도 애플리케이션이 기동되도록
 * 빈 문자열이면 기본 생성자를 사용합니다. 실제 업로드 호출 시에는 값이 반드시 필요합니다.
 */
@Configuration
public class CloudinaryConfig {

	@Value("${cloudinary.url:}")
	private String cloudinaryUrl;

	@Bean
	public Cloudinary cloudinary() {
		if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
			return new Cloudinary();
		}
		return new Cloudinary(cloudinaryUrl);
	}
}

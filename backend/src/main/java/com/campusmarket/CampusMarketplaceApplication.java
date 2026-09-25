package com.campusmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class CampusMarketplaceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CampusMarketplaceApplication.class, args);
	}
}

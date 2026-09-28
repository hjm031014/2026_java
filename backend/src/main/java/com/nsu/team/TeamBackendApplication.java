package com.nsu.team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class TeamBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TeamBackendApplication.class, args);
	}

}

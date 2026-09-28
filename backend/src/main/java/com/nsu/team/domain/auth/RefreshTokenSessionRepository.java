package com.nsu.team.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {
}

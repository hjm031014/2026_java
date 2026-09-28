package com.nsu.team.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SalePostRepository extends JpaRepository<SalePost, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SalePost p where p.id = :id")
    Optional<SalePost> findByIdForUpdate(@Param("id") Long id);
}

package com.nsu.team.chat;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByPostIdAndBuyerId(Long postId, Long buyerId);

    @Query("""
            select r from ChatRoom r
            join fetch r.post p
            join fetch r.seller
            join fetch r.buyer
            where (r.seller.id = :userId or r.buyer.id = :userId)
              and (:cursorTime is null or r.updatedAt < :cursorTime
                   or (r.updatedAt = :cursorTime and r.id < :cursorId))
            order by r.updatedAt desc, r.id desc
            """)
    List<ChatRoom> findPage(@Param("userId") Long userId,
                            @Param("cursorTime") Instant cursorTime,
                            @Param("cursorId") Long cursorId,
                            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r join fetch r.seller join fetch r.buyer join fetch r.post where r.id = :id")
    Optional<ChatRoom> findByIdForUpdate(@Param("id") Long id);

    @Query("select r from ChatRoom r join fetch r.seller join fetch r.buyer join fetch r.post where r.id = :id")
    Optional<ChatRoom> findDetailedById(@Param("id") Long id);
}

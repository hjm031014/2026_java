package com.nsu.team.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("select m from ChatMessage m join fetch m.sender where m.room.id = :roomId and m.clientMessageId = :clientId")
    Optional<ChatMessage> findByClientId(@Param("roomId") Long roomId, @Param("clientId") String clientId);

    Optional<ChatMessage> findFirstByRoomIdOrderBySequenceDesc(Long roomId);

    @Query("""
            select m from ChatMessage m join fetch m.sender
            where m.room.id = :roomId
              and (:cursorTime is null or m.createdAt < :cursorTime
                   or (m.createdAt = :cursorTime and m.id < :cursorId))
            order by m.createdAt desc, m.id desc
            """)
    List<ChatMessage> findHistory(@Param("roomId") Long roomId,
                                  @Param("cursorTime") Instant cursorTime,
                                  @Param("cursorId") Long cursorId,
                                  Pageable pageable);

    @Query("""
            select m from ChatMessage m join fetch m.sender
            where m.room.id = :roomId and m.sequence > :afterSequence
            order by m.sequence asc
            """)
    List<ChatMessage> findAfter(@Param("roomId") Long roomId,
                                @Param("afterSequence") long afterSequence,
                                Pageable pageable);
}

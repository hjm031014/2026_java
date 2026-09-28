package com.nsu.team.comment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query("""
            select c from Comment c
            join fetch c.author
            where c.post.id = :postId and c.deletedAt is null
              and (:cursorTime is null or c.createdAt < :cursorTime
                   or (c.createdAt = :cursorTime and c.id < :cursorId))
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findPage(@Param("postId") Long postId,
                           @Param("cursorTime") Instant cursorTime,
                           @Param("cursorId") Long cursorId,
                           Pageable pageable);

    @Query("select c from Comment c join fetch c.author where c.id = :id and c.deletedAt is null")
    Optional<Comment> findActiveById(@Param("id") Long id);
}

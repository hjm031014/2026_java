package com.nsu.team.favorite;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {
    @Query("""
            select f from Favorite f
            join fetch f.post p
            join fetch p.seller
            left join fetch p.category
            left join fetch p.meetupLocation
            where f.user.id = :userId and p.status <> com.nsu.team.post.SalePost.Status.DELETED
              and (:cursorTime is null or f.createdAt < :cursorTime
                   or (f.createdAt = :cursorTime and p.id < :cursorId))
            order by f.createdAt desc, p.id desc
            """)
    List<Favorite> findPage(@Param("userId") Long userId,
                            @Param("cursorTime") Instant cursorTime,
                            @Param("cursorId") Long cursorId,
                            Pageable pageable);

    long countByIdPostId(Long postId);
}

package com.nsu.team.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalePostRepository extends JpaRepository<SalePost, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SalePost p where p.id = :id")
    Optional<SalePost> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p from SalePost p
            join fetch p.seller
            join fetch p.category
            join fetch p.meetupLocation
            where p.id = :id and p.status <> com.nsu.team.post.SalePost.Status.DELETED
            """)
    Optional<SalePost> findVisibleDetail(@Param("id") Long id);

    @Query("""
            select p from SalePost p
            join fetch p.seller
            join fetch p.category
            join fetch p.meetupLocation
            where p.status <> com.nsu.team.post.SalePost.Status.DELETED
              and (:q is null or lower(p.title) like lower(concat('%', :q, '%'))
                   or lower(p.description) like lower(concat('%', :q, '%')))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:status is null or p.status = :status)
              and (:cursorTime is null or p.createdAt < :cursorTime
                   or (p.createdAt = :cursorTime and p.id < :cursorId))
            order by p.createdAt desc, p.id desc
            """)
    List<SalePost> findLatestPage(@Param("q") String q,
                                  @Param("categoryId") Long categoryId,
                                  @Param("status") SalePost.Status status,
                                  @Param("cursorTime") Instant cursorTime,
                                  @Param("cursorId") Long cursorId,
                                  Pageable pageable);

    @Query("""
            select p from SalePost p
            join fetch p.seller
            join fetch p.category
            join fetch p.meetupLocation
            where p.status <> com.nsu.team.post.SalePost.Status.DELETED
              and (:q is null or lower(p.title) like lower(concat('%', :q, '%'))
                   or lower(p.description) like lower(concat('%', :q, '%')))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:status is null or p.status = :status)
              and (:cursorPrice is null or p.price > :cursorPrice
                   or (p.price = :cursorPrice and p.id > :cursorId))
            order by p.price asc, p.id asc
            """)
    List<SalePost> findPriceAscPage(@Param("q") String q,
                                    @Param("categoryId") Long categoryId,
                                    @Param("status") SalePost.Status status,
                                    @Param("cursorPrice") BigDecimal cursorPrice,
                                    @Param("cursorId") Long cursorId,
                                    Pageable pageable);

    @Query("""
            select p from SalePost p
            join fetch p.seller
            join fetch p.category
            join fetch p.meetupLocation
            where p.status <> com.nsu.team.post.SalePost.Status.DELETED
              and (:q is null or lower(p.title) like lower(concat('%', :q, '%'))
                   or lower(p.description) like lower(concat('%', :q, '%')))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:status is null or p.status = :status)
              and (:cursorPrice is null or p.price < :cursorPrice
                   or (p.price = :cursorPrice and p.id < :cursorId))
            order by p.price desc, p.id desc
            """)
    List<SalePost> findPriceDescPage(@Param("q") String q,
                                     @Param("categoryId") Long categoryId,
                                     @Param("status") SalePost.Status status,
                                     @Param("cursorPrice") BigDecimal cursorPrice,
                                     @Param("cursorId") Long cursorId,
                                     Pageable pageable);
}

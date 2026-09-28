package com.nsu.team.domain.image;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
	@Query("""
			select i from PostImage i
			join fetch i.uploader
			where i.id in :ids
			""")
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<PostImage> findAllDetailedByIdIn(@Param("ids") Collection<Long> ids);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from PostImage i join fetch i.uploader where i.id = :id")
	Optional<PostImage> findByIdForUpdate(@Param("id") Long id);

	@Query("""
			select i from PostImage i
			where i.post.id in :postIds
			order by i.post.id asc, i.displayOrder asc, i.id asc
			""")
	List<PostImage> findAllByPostIds(@Param("postIds") Collection<Long> postIds);

	List<PostImage> findAllByPostIdOrderByDisplayOrderAscIdAsc(Long postId);
}

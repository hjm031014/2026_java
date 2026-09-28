package com.nsu.team.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostViewEventRepository extends JpaRepository<PostViewEvent, Long> {
	Optional<PostViewEvent> findByPostIdAndViewerKeyHash(Long postId, String viewerKeyHash);
}

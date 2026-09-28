package com.nsu.team.comment;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.response.PageResponse;
import com.nsu.team.common.util.CursorCodec;
import com.nsu.team.common.util.KeysetPageFactory;
import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

	private final CommentRepository commentRepository;
	private final SalePostRepository salePostRepository;
	private final UserRepository userRepository;
	private final KeysetPageFactory keysetPageFactory;

	public PageResponse<CommentDtos.Response> list(Long postId, String cursor, int limit) {
		requireVisiblePost(postId);
		CursorCodec.KeysetCursor decodedCursor = CursorCodec.decodeKeysetNullable(cursor);
		Instant cursorTime = decodedCursor == null ? null : decodedCursor.time();
		Long cursorId = decodedCursor == null ? null : decodedCursor.id();
		List<Comment> comments = commentRepository.findPage(
				postId, cursorTime, cursorId, PageRequest.of(0, limit + 1));

		return keysetPageFactory.create(
				comments, limit, Comment::getCreatedAt, Comment::getId, CommentDtos.Response::from);
	}

	@Transactional
	public CommentDtos.Response create(Long postId, Long authorId, CommentDtos.CreateRequest request) {
		SalePost post = requireVisiblePost(postId);
		User author = userRepository.findById(authorId).orElseThrow(BusinessException::unauthenticated);
		Comment comment = commentRepository.save(new Comment(post, author, request.content().trim()));
		return CommentDtos.Response.from(comment);
	}

	@Transactional
	public void delete(Long commentId, Long requesterId) {
		Comment comment = commentRepository.findActiveById(commentId).orElseThrow(BusinessException::notFound);
		if (!comment.isWrittenBy(requesterId)) {
			throw BusinessException.forbidden("댓글 작성자만 삭제할 수 있습니다.");
		}
		comment.delete();
	}

	private SalePost requireVisiblePost(Long postId) {
		SalePost post = salePostRepository.findById(postId).orElseThrow(BusinessException::notFound);
		if (post.isDeleted()) throw BusinessException.notFound();
		return post;
	}
}

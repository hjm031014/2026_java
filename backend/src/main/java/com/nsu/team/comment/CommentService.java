package com.nsu.team.comment;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.response.PageResponse;
import com.nsu.team.common.util.CursorCodec;
import com.nsu.team.common.util.KeysetPageFactory;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import com.nsu.team.user.CurrentUserProvider;
import com.nsu.team.domain.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CommentService {
    private final CommentRepository comments;
    private final SalePostRepository posts;
    private final CurrentUserProvider currentUser;
    private final KeysetPageFactory pageFactory;

    public CommentService(CommentRepository comments, SalePostRepository posts,
                          CurrentUserProvider currentUser, KeysetPageFactory pageFactory) {
        this.comments = comments;
        this.posts = posts;
        this.currentUser = currentUser;
        this.pageFactory = pageFactory;
    }

    public PageResponse<CommentDtos.Response> list(Long postId, String cursor, int limit) {
        requireVisiblePost(postId);
        CursorCodec.KeysetCursor decoded = CursorCodec.decodeKeysetNullable(cursor);
        Instant cursorTime = decoded == null ? null : decoded.time();
        Long cursorId = decoded == null ? null : decoded.id();
        List<Comment> found = comments.findPage(
                postId, cursorTime, cursorId, PageRequest.of(0, limit + 1));
        return pageFactory.create(
                found, limit, Comment::getCreatedAt, Comment::getId, CommentDtos.Response::from);
    }

    @Transactional
    public CommentDtos.Response create(Long postId, CommentDtos.CreateRequest request) {
        SalePost post = requireVisiblePost(postId);
        User author = currentUser.require();
        Comment saved = comments.save(new Comment(post, author, request.content().trim()));
        return CommentDtos.Response.from(saved);
    }

    @Transactional
    public void delete(Long commentId) {
        User user = currentUser.require();
        Comment comment = comments.findActiveById(commentId).orElseThrow(BusinessException::notFound);
        if (!comment.isWrittenBy(user)) {
            throw BusinessException.forbidden("댓글 작성자만 삭제할 수 있습니다.");
        }
        comment.delete();
    }

    private SalePost requireVisiblePost(Long postId) {
        SalePost post = posts.findById(postId).orElseThrow(BusinessException::notFound);
        if (post.isDeleted()) throw BusinessException.notFound();
        return post;
    }
}

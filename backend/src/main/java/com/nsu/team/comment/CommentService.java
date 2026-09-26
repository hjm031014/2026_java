package com.nsu.team.comment;

import com.nsu.team.common.ApiException;
import com.nsu.team.common.CursorCodec;
import com.nsu.team.common.KeysetPageFactory;
import com.nsu.team.communication.dto.PagedItems;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import com.nsu.team.user.CurrentUserProvider;
import com.nsu.team.user.UserAccount;
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
    private final CursorCodec cursors;
    private final KeysetPageFactory pageFactory;

    public CommentService(CommentRepository comments, SalePostRepository posts,
                          CurrentUserProvider currentUser, CursorCodec cursors,
                          KeysetPageFactory pageFactory) {
        this.comments = comments;
        this.posts = posts;
        this.currentUser = currentUser;
        this.cursors = cursors;
        this.pageFactory = pageFactory;
    }

    public PagedItems<CommentDtos.Response> list(Long postId, String cursor, int limit) {
        requireVisiblePost(postId);
        CursorCodec.Cursor decoded = cursors.decodeNullable(cursor);
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
        UserAccount author = currentUser.require();
        Comment saved = comments.save(new Comment(post, author, request.content().trim()));
        return CommentDtos.Response.from(saved);
    }

    @Transactional
    public void delete(Long commentId) {
        UserAccount user = currentUser.require();
        Comment comment = comments.findActiveById(commentId).orElseThrow(ApiException::notFound);
        if (!comment.isWrittenBy(user)) {
            throw ApiException.forbidden("댓글 작성자만 삭제할 수 있습니다.");
        }
        comment.delete();
    }

    private SalePost requireVisiblePost(Long postId) {
        SalePost post = posts.findById(postId).orElseThrow(ApiException::notFound);
        if (post.isDeleted()) throw ApiException.notFound();
        return post;
    }
}

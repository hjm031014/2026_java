package com.nsu.team.favorite;

import com.nsu.team.common.ApiException;
import com.nsu.team.common.CursorCodec;
import com.nsu.team.communication.dto.PageInfo;
import com.nsu.team.communication.dto.PagedItems;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import com.nsu.team.user.CurrentUserProvider;
import com.nsu.team.domain.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FavoriteService {
    private final FavoriteRepository favorites;
    private final SalePostRepository posts;
    private final CurrentUserProvider currentUser;
    private final CursorCodec cursors;

    public FavoriteService(FavoriteRepository favorites, SalePostRepository posts,
                           CurrentUserProvider currentUser, CursorCodec cursors) {
        this.favorites = favorites;
        this.posts = posts;
        this.currentUser = currentUser;
        this.cursors = cursors;
    }

    @Transactional
    public void add(Long postId) {
        User user = currentUser.require();
        SalePost post = requireVisiblePost(postId);
        FavoriteId id = new FavoriteId(user.getId(), postId);
        if (!favorites.existsById(id)) favorites.save(new Favorite(user, post));
    }

    @Transactional
    public void remove(Long postId) {
        User user = currentUser.require();
        favorites.deleteById(new FavoriteId(user.getId(), postId));
    }

    public PagedItems<FavoriteDtos.FavoriteItem> list(String cursor, int limit) {
        User user = currentUser.require();
        Instant cursorTime = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.Cursor decoded = cursors.decode(cursor);
            cursorTime = decoded.time();
            cursorId = decoded.id();
        }
        List<Favorite> found = new ArrayList<>(favorites.findPage(
                user.getId(), cursorTime, cursorId, PageRequest.of(0, limit + 1)));
        boolean hasNext = found.size() > limit;
        if (hasNext) found.remove(found.size() - 1);
        String next = hasNext && !found.isEmpty()
                ? cursors.encode(found.get(found.size() - 1).getCreatedAt(),
                    found.get(found.size() - 1).getPost().getId())
                : null;
        List<FavoriteDtos.FavoriteItem> items = found.stream()
                .map(favorite -> new FavoriteDtos.FavoriteItem(
                        FavoriteDtos.PostSummary.from(favorite.getPost(),
                                favorites.countByIdPostId(favorite.getPost().getId())),
                        favorite.getCreatedAt()))
                .toList();
        return new PagedItems<>(items, new PageInfo(next, hasNext));
    }

    private SalePost requireVisiblePost(Long postId) {
        SalePost post = posts.findById(postId).orElseThrow(ApiException::notFound);
        if (post.isDeleted()) throw ApiException.notFound();
        return post;
    }
}

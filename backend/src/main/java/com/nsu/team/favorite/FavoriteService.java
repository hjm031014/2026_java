package com.nsu.team.favorite;

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
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FavoriteService {
    private final FavoriteRepository favorites;
    private final SalePostRepository posts;
    private final CurrentUserProvider currentUser;
    private final CursorCodec cursors;
    private final KeysetPageFactory pageFactory;

    public FavoriteService(FavoriteRepository favorites, SalePostRepository posts,
                           CurrentUserProvider currentUser, CursorCodec cursors,
                           KeysetPageFactory pageFactory) {
        this.favorites = favorites;
        this.posts = posts;
        this.currentUser = currentUser;
        this.cursors = cursors;
        this.pageFactory = pageFactory;
    }

    @Transactional
    public void add(Long postId) {
        UserAccount user = currentUser.requireForUpdate();
        SalePost post = requireVisiblePost(postId);
        FavoriteId id = new FavoriteId(user.getId(), postId);
        if (!favorites.existsById(id)) favorites.save(new Favorite(user, post));
    }

    @Transactional
    public void remove(Long postId) {
        UserAccount user = currentUser.require();
        favorites.findById(new FavoriteId(user.getId(), postId)).ifPresent(favorites::delete);
    }

    public PagedItems<FavoriteDtos.FavoriteItem> list(String cursor, int limit) {
        UserAccount user = currentUser.require();
        CursorCodec.Cursor decoded = cursors.decodeNullable(cursor);
        Instant cursorTime = decoded == null ? null : decoded.time();
        Long cursorId = decoded == null ? null : decoded.id();
        List<Favorite> found = favorites.findPage(
                user.getId(), cursorTime, cursorId, PageRequest.of(0, limit + 1));

        Map<Long, Long> countsByPostId = loadFavoriteCounts(found);
        return pageFactory.create(
                found,
                limit,
                Favorite::getCreatedAt,
                favorite -> favorite.getPost().getId(),
                favorite -> new FavoriteDtos.FavoriteItem(
                        FavoriteDtos.PostSummary.from(
                                favorite.getPost(), countsByPostId.getOrDefault(favorite.getPost().getId(), 0L)),
                        favorite.getCreatedAt()));
    }

    private Map<Long, Long> loadFavoriteCounts(List<Favorite> favoritesPage) {
        List<Long> postIds = favoritesPage.stream()
                .map(favorite -> favorite.getPost().getId())
                .distinct()
                .toList();
        if (postIds.isEmpty()) return Map.of();
        return favorites.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(FavoriteCount::getPostId, FavoriteCount::getFavoriteCount));
    }

    private SalePost requireVisiblePost(Long postId) {
        SalePost post = posts.findById(postId).orElseThrow(ApiException::notFound);
        if (post.isDeleted()) throw ApiException.notFound();
        return post;
    }
}

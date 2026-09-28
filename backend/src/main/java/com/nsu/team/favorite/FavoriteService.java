package com.nsu.team.favorite;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.response.PageResponse;
import com.nsu.team.common.util.CursorCodec;
import com.nsu.team.common.util.KeysetPageFactory;
import com.nsu.team.domain.user.User;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import com.nsu.team.user.CurrentUserProvider;
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
	private final KeysetPageFactory pageFactory;

	public FavoriteService(FavoriteRepository favorites, SalePostRepository posts,
			CurrentUserProvider currentUser, KeysetPageFactory pageFactory) {
		this.favorites = favorites;
		this.posts = posts;
		this.currentUser = currentUser;
		this.pageFactory = pageFactory;
	}

	@Transactional
	public void add(Long postId) {
		User user = currentUser.requireForUpdate();
		SalePost post = requireVisiblePost(postId);
		FavoriteId id = new FavoriteId(user.getId(), postId);
		if (!favorites.existsById(id)) favorites.save(new Favorite(user, post));
	}

	@Transactional
	public void remove(Long postId) {
		User user = currentUser.require();
		favorites.findById(new FavoriteId(user.getId(), postId)).ifPresent(favorites::delete);
	}

	public PageResponse<FavoriteDtos.FavoriteItem> list(String cursor, int limit) {
		User user = currentUser.require();
		CursorCodec.KeysetCursor decoded = CursorCodec.decodeKeysetNullable(cursor);
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
		SalePost post = posts.findById(postId).orElseThrow(BusinessException::notFound);
		if (post.isDeleted()) throw BusinessException.notFound();
		return post;
	}
}

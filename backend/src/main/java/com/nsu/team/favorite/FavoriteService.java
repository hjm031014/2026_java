package com.nsu.team.favorite;

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
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;
	private final SalePostRepository salePostRepository;
	private final UserRepository userRepository;
	private final KeysetPageFactory keysetPageFactory;

	@Transactional
	public void add(Long postId, Long userId) {
		User user = userRepository.findByIdForUpdate(userId).orElseThrow(BusinessException::unauthenticated);
		SalePost post = requireVisiblePost(postId);
		FavoriteId favoriteId = new FavoriteId(userId, postId);
		if (!favoriteRepository.existsById(favoriteId)) {
			favoriteRepository.save(new Favorite(user, post));
		}
	}

	@Transactional
	public void remove(Long postId, Long userId) {
		FavoriteId favoriteId = new FavoriteId(userId, postId);
		favoriteRepository.findById(favoriteId).ifPresent(favoriteRepository::delete);
	}

	public PageResponse<FavoriteDtos.FavoriteItem> list(Long userId, String cursor, int limit) {
		CursorCodec.KeysetCursor decodedCursor = CursorCodec.decodeKeysetNullable(cursor);
		Instant cursorTime = decodedCursor == null ? null : decodedCursor.time();
		Long cursorId = decodedCursor == null ? null : decodedCursor.id();
		List<Favorite> favorites = favoriteRepository.findPage(
				userId, cursorTime, cursorId, PageRequest.of(0, limit + 1));
		Map<Long, Long> favoriteCountsByPostId = loadFavoriteCounts(favorites);

		return keysetPageFactory.create(
				favorites,
				limit,
				Favorite::getCreatedAt,
				favorite -> favorite.getPost().getId(),
				favorite -> new FavoriteDtos.FavoriteItem(
						FavoriteDtos.PostSummary.from(
								favorite.getPost(),
								favoriteCountsByPostId.getOrDefault(favorite.getPost().getId(), 0L)),
						favorite.getCreatedAt()));
	}

	private Map<Long, Long> loadFavoriteCounts(List<Favorite> favorites) {
		List<Long> postIds = favorites.stream()
				.map(favorite -> favorite.getPost().getId())
				.distinct()
				.toList();
		if (postIds.isEmpty()) return Map.of();
		return favoriteRepository.countByPostIds(postIds).stream()
				.collect(Collectors.toMap(FavoriteCount::getPostId, FavoriteCount::getFavoriteCount));
	}

	private SalePost requireVisiblePost(Long postId) {
		SalePost post = salePostRepository.findById(postId).orElseThrow(BusinessException::notFound);
		if (post.isDeleted()) throw BusinessException.notFound();
		return post;
	}
}

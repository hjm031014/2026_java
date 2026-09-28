package com.nsu.team.common.util;

import com.nsu.team.common.response.PageResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Component
public class KeysetPageFactory {
	public <T, R> PageResponse<R> create(
			List<T> fetched,
			int limit,
			Function<T, Instant> timeExtractor,
			ToLongFunction<T> idExtractor,
			Function<T, R> mapper) {
		List<T> content = new ArrayList<>(fetched);
		boolean hasNext = content.size() > limit;
		if (hasNext) content.remove(content.size() - 1);

		String nextCursor = null;
		if (hasNext && !content.isEmpty()) {
			T last = content.get(content.size() - 1);
			nextCursor = CursorCodec.encode(timeExtractor.apply(last), idExtractor.applyAsLong(last));
		}

		return PageResponse.of(content.stream().map(mapper).toList(), nextCursor, hasNext);
	}
}

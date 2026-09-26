package com.nsu.team.common;

import com.nsu.team.communication.dto.PageInfo;
import com.nsu.team.communication.dto.PagedItems;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Component
public class KeysetPageFactory {
    private final CursorCodec cursorCodec;

    public KeysetPageFactory(CursorCodec cursorCodec) {
        this.cursorCodec = cursorCodec;
    }

    public <T, R> PagedItems<R> create(
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
            nextCursor = cursorCodec.encode(timeExtractor.apply(last), idExtractor.applyAsLong(last));
        }

        return new PagedItems<>(content.stream().map(mapper).toList(), new PageInfo(nextCursor, hasNext));
    }
}

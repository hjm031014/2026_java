package com.nsu.team.common;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Component
public class CursorCodec {
    public String encode(Instant time, long id) {
        // PostgreSQL timestamptz stores microseconds. Normalizing prevents a just-persisted
        // entity's nanoseconds from making the boundary row reappear on the next page.
        String raw = time.truncatedTo(ChronoUnit.MICROS) + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] values = raw.split("\\|", -1);
            if (values.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(values[0]), Long.parseLong(values[1]));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "유효하지 않은 커서입니다.");
        }
    }

    public Cursor decodeNullable(String cursor) {
        return cursor == null || cursor.isBlank() ? null : decode(cursor);
    }

    public record Cursor(Instant time, long id) {}
}

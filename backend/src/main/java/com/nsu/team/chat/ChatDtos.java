package com.nsu.team.chat;

import com.nsu.team.communication.dto.PublicUserResponse;
import com.nsu.team.common.response.PageInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() {}

    public record CreateRoomRequest(@NotNull @Positive Long postId) {}

    public record SendMessageRequest(
            @NotNull UUID clientMessageId,
            @NotBlank @Size(max = 4000) String content
    ) {}

    public record PostReference(String id, String title, String status, boolean postDeleted) {}

    public record MessageResponse(
            String id,
            String roomId,
            PublicUserResponse sender,
            long sequence,
            String clientMessageId,
            String content,
            Instant createdAt
    ) {
        static MessageResponse from(ChatMessage message) {
            return new MessageResponse(
                    message.getId().toString(), message.getRoom().getId().toString(),
                    PublicUserResponse.from(message.getSender()), message.getSequence(),
                    message.getClientMessageId(), message.getContent(), message.getCreatedAt());
        }
    }

    public record RoomResponse(
            String id,
            PostReference post,
            PublicUserResponse otherUser,
            MessageResponse lastMessage,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public sealed interface MessagePage permits HistoryMessages, NewMessages {}

    public record HistoryMessages(List<MessageResponse> items, PageInfo page) implements MessagePage {}

    public record NewMessages(
            List<MessageResponse> items,
            long nextAfterSequence,
            boolean hasMore
    ) implements MessagePage {}

    public record CreationResult<T>(T value, boolean created) {}
}

package com.nsu.team.chat;

import com.nsu.team.common.ApiException;
import com.nsu.team.common.CursorCodec;
import com.nsu.team.communication.dto.PageInfo;
import com.nsu.team.communication.dto.PagedItems;
import com.nsu.team.communication.dto.PublicUserResponse;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import com.nsu.team.user.CurrentUserProvider;
import com.nsu.team.user.UserAccount;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChatService {
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final SalePostRepository posts;
    private final CurrentUserProvider currentUser;
    private final CursorCodec cursors;

    public ChatService(ChatRoomRepository rooms, ChatMessageRepository messages,
                       SalePostRepository posts, CurrentUserProvider currentUser, CursorCodec cursors) {
        this.rooms = rooms;
        this.messages = messages;
        this.posts = posts;
        this.currentUser = currentUser;
        this.cursors = cursors;
    }

    @Transactional
    public ChatDtos.CreationResult<ChatDtos.RoomResponse> createRoom(ChatDtos.CreateRoomRequest request) {
        UserAccount buyer = currentUser.require();
        SalePost post = posts.findByIdForUpdate(request.postId()).orElseThrow(ApiException::notFound);
        if (post.isDeleted()) throw ApiException.notFound();
        if (post.getSeller().getId().equals(buyer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "본인의 판매글에는 채팅방을 만들 수 없습니다.");
        }
        return rooms.findByPostIdAndBuyerId(post.getId(), buyer.getId())
                .map(room -> new ChatDtos.CreationResult<>(toRoom(room, buyer), false))
                .orElseGet(() -> {
                    ChatRoom room = rooms.saveAndFlush(new ChatRoom(post, buyer));
                    return new ChatDtos.CreationResult<>(toRoom(room, buyer), true);
                });
    }

    public PagedItems<ChatDtos.RoomResponse> listRooms(String cursor, int limit) {
        UserAccount user = currentUser.require();
        Instant cursorTime = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.Cursor decoded = cursors.decode(cursor);
            cursorTime = decoded.time();
            cursorId = decoded.id();
        }
        List<ChatRoom> found = new ArrayList<>(rooms.findPage(
                user.getId(), cursorTime, cursorId, PageRequest.of(0, limit + 1)));
        boolean hasNext = found.size() > limit;
        if (hasNext) found.remove(found.size() - 1);
        String next = hasNext && !found.isEmpty()
                ? cursors.encode(found.get(found.size() - 1).getUpdatedAt(), found.get(found.size() - 1).getId())
                : null;
        return new PagedItems<>(found.stream().map(room -> toRoom(room, user)).toList(),
                new PageInfo(next, hasNext));
    }

    public Object listMessages(Long roomId, String cursor, Long afterSequence, int limit) {
        UserAccount user = currentUser.require();
        requireParticipant(roomId, user, false);
        if (cursor != null && afterSequence != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "cursor와 afterSequence는 함께 사용할 수 없습니다.");
        }
        if (afterSequence != null) return listNewMessages(roomId, afterSequence, limit);
        return listHistory(roomId, cursor, limit);
    }

    @Transactional
    public ChatDtos.CreationResult<ChatDtos.MessageResponse> sendMessage(
            Long roomId, ChatDtos.SendMessageRequest request) {
        UserAccount sender = currentUser.require();
        ChatRoom room = requireParticipant(roomId, sender, true);
        String clientId = request.clientMessageId().toString();
        var existing = messages.findByClientId(roomId, clientId);
        if (existing.isPresent()) {
            ChatMessage message = existing.get();
            if (!message.getSender().getId().equals(sender.getId())
                    || !message.getContent().equals(request.content().trim())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "같은 clientMessageId가 다른 메시지에 사용되었습니다.");
            }
            return new ChatDtos.CreationResult<>(ChatDtos.MessageResponse.from(message), false);
        }
        Instant sentAt = Instant.now();
        long sequence = room.nextSequence(sentAt);
        ChatMessage saved = messages.saveAndFlush(new ChatMessage(
                room, sender, sequence, clientId, request.content().trim(), sentAt));
        return new ChatDtos.CreationResult<>(ChatDtos.MessageResponse.from(saved), true);
    }

    private PagedItems<ChatDtos.MessageResponse> listHistory(Long roomId, String cursor, int limit) {
        Instant cursorTime = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.Cursor decoded = cursors.decode(cursor);
            cursorTime = decoded.time();
            cursorId = decoded.id();
        }
        List<ChatMessage> found = new ArrayList<>(messages.findHistory(
                roomId, cursorTime, cursorId, PageRequest.of(0, limit + 1)));
        boolean hasNext = found.size() > limit;
        if (hasNext) found.remove(found.size() - 1);
        String next = hasNext && !found.isEmpty()
                ? cursors.encode(found.get(found.size() - 1).getCreatedAt(), found.get(found.size() - 1).getId())
                : null;
        return new PagedItems<>(found.stream().map(ChatDtos.MessageResponse::from).toList(),
                new PageInfo(next, hasNext));
    }

    private ChatDtos.NewMessages listNewMessages(Long roomId, long afterSequence, int limit) {
        if (afterSequence < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "afterSequence는 0 이상이어야 합니다.");
        }
        List<ChatMessage> found = new ArrayList<>(messages.findAfter(
                roomId, afterSequence, PageRequest.of(0, limit + 1)));
        boolean hasMore = found.size() > limit;
        if (hasMore) found.remove(found.size() - 1);
        long next = found.isEmpty() ? afterSequence : found.get(found.size() - 1).getSequence();
        return new ChatDtos.NewMessages(
                found.stream().map(ChatDtos.MessageResponse::from).toList(), next, hasMore);
    }

    private ChatRoom requireParticipant(Long roomId, UserAccount user, boolean lock) {
        ChatRoom room = (lock ? rooms.findByIdForUpdate(roomId) : rooms.findDetailedById(roomId))
                .orElseThrow(ApiException::notFound);
        if (!room.getSeller().getId().equals(user.getId()) && !room.getBuyer().getId().equals(user.getId())) {
            throw ApiException.notFound();
        }
        return room;
    }

    private ChatDtos.RoomResponse toRoom(ChatRoom room, UserAccount viewer) {
        UserAccount other = room.getSeller().getId().equals(viewer.getId()) ? room.getBuyer() : room.getSeller();
        SalePost post = room.getPost();
        ChatDtos.PostReference postReference = new ChatDtos.PostReference(
                post.getId().toString(), post.getTitle(), post.getStatus().name(), post.isDeleted());
        ChatDtos.MessageResponse lastMessage = messages.findFirstByRoomIdOrderBySequenceDesc(room.getId())
                .map(ChatDtos.MessageResponse::from).orElse(null);
        return new ChatDtos.RoomResponse(
                room.getId().toString(), postReference, PublicUserResponse.from(other), lastMessage,
                room.getCreatedAt(), room.getUpdatedAt());
    }
}

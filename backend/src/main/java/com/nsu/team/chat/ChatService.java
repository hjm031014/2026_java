package com.nsu.team.chat;

import com.nsu.team.common.ApiException;
import com.nsu.team.common.CursorCodec;
import com.nsu.team.common.KeysetPageFactory;
import com.nsu.team.communication.dto.PagedItems;
import com.nsu.team.communication.dto.PublicUserResponse;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import com.nsu.team.user.CurrentUserProvider;
import com.nsu.team.user.UserAccount;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ChatService {
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final SalePostRepository posts;
    private final CurrentUserProvider currentUser;
    private final CursorCodec cursors;
    private final KeysetPageFactory pageFactory;

    public ChatService(ChatRoomRepository rooms, ChatMessageRepository messages,
                       SalePostRepository posts, CurrentUserProvider currentUser, CursorCodec cursors,
                       KeysetPageFactory pageFactory) {
        this.rooms = rooms;
        this.messages = messages;
        this.posts = posts;
        this.currentUser = currentUser;
        this.cursors = cursors;
        this.pageFactory = pageFactory;
    }

    @Transactional
    public ChatDtos.CreationResult<ChatDtos.RoomResponse> createRoom(ChatDtos.CreateRoomRequest request) {
        UserAccount buyer = currentUser.require();
        SalePost post = posts.findByIdForUpdate(request.postId()).orElseThrow(ApiException::notFound);
        if (post.isDeleted()) throw ApiException.notFound();
        if (post.getSeller().getId().equals(buyer.getId())) {
            throw ApiException.forbidden("본인의 판매글에는 채팅방을 만들 수 없습니다.");
        }
        return rooms.findByPostIdAndBuyerId(post.getId(), buyer.getId())
                .map(room -> new ChatDtos.CreationResult<>(toRoom(room, buyer, findLastMessage(room.getId())), false))
                .orElseGet(() -> {
                    ChatRoom room = rooms.saveAndFlush(new ChatRoom(post, buyer));
                    return new ChatDtos.CreationResult<>(toRoom(room, buyer, null), true);
                });
    }

    public PagedItems<ChatDtos.RoomResponse> listRooms(String cursor, int limit) {
        UserAccount user = currentUser.require();
        CursorCodec.Cursor decoded = cursors.decodeNullable(cursor);
        Instant cursorTime = decoded == null ? null : decoded.time();
        Long cursorId = decoded == null ? null : decoded.id();
        List<ChatRoom> found = rooms.findPage(
                user.getId(), cursorTime, cursorId, PageRequest.of(0, limit + 1));
        Map<Long, ChatMessage> lastMessages = loadLastMessages(found);
        return pageFactory.create(
                found,
                limit,
                ChatRoom::getUpdatedAt,
                ChatRoom::getId,
                room -> toRoom(room, user, lastMessages.get(room.getId())));
    }

    public ChatDtos.MessagePage listMessages(Long roomId, String cursor, Long afterSequence, int limit) {
        UserAccount user = currentUser.require();
        requireParticipant(roomId, user, false);
        if (cursor != null && afterSequence != null) {
            throw ApiException.validation("cursor와 afterSequence는 함께 사용할 수 없습니다.");
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
            if (!message.hasSamePayload(sender, request.content().trim())) {
                throw ApiException.conflict("IDEMPOTENCY_CONFLICT",
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

    private ChatDtos.HistoryMessages listHistory(Long roomId, String cursor, int limit) {
        CursorCodec.Cursor decoded = cursors.decodeNullable(cursor);
        Instant cursorTime = decoded == null ? null : decoded.time();
        Long cursorId = decoded == null ? null : decoded.id();
        List<ChatMessage> found = messages.findHistory(
                roomId, cursorTime, cursorId, PageRequest.of(0, limit + 1));
        PagedItems<ChatDtos.MessageResponse> page = pageFactory.create(
                found, limit, ChatMessage::getCreatedAt, ChatMessage::getId, ChatDtos.MessageResponse::from);
        return new ChatDtos.HistoryMessages(page.items(), page.page());
    }

    private ChatDtos.NewMessages listNewMessages(Long roomId, long afterSequence, int limit) {
        if (afterSequence < 0) {
            throw ApiException.validation("afterSequence는 0 이상이어야 합니다.");
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
        if (!room.hasParticipant(user)) {
            throw ApiException.notFound();
        }
        return room;
    }

    private ChatDtos.RoomResponse toRoom(ChatRoom room, UserAccount viewer, ChatMessage lastMessage) {
        UserAccount other = room.otherParticipant(viewer);
        SalePost post = room.getPost();
        ChatDtos.PostReference postReference = new ChatDtos.PostReference(
                post.getId().toString(), post.getTitle(), post.getStatus().name(), post.isDeleted());
        ChatDtos.MessageResponse lastMessageResponse = lastMessage == null
                ? null : ChatDtos.MessageResponse.from(lastMessage);
        return new ChatDtos.RoomResponse(
                room.getId().toString(), postReference, PublicUserResponse.from(other), lastMessageResponse,
                room.getCreatedAt(), room.getUpdatedAt());
    }

    private ChatMessage findLastMessage(Long roomId) {
        return messages.findFirstByRoomIdOrderBySequenceDesc(roomId).orElse(null);
    }

    private Map<Long, ChatMessage> loadLastMessages(List<ChatRoom> roomPage) {
        List<Long> roomIds = roomPage.stream().map(ChatRoom::getId).toList();
        if (roomIds.isEmpty()) return Map.of();
        return messages.findLastByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(message -> message.getRoom().getId(), Function.identity()));
    }
}

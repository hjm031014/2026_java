package com.nsu.team.chat;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.common.response.PageResponse;
import com.nsu.team.common.util.CursorCodec;
import com.nsu.team.common.util.KeysetPageFactory;
import com.nsu.team.communication.dto.PublicUserResponse;
import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final SalePostRepository salePostRepository;
	private final UserRepository userRepository;
	private final KeysetPageFactory keysetPageFactory;

	@Transactional
	public ChatDtos.CreationResult<ChatDtos.RoomResponse> createRoom(
			Long buyerId, ChatDtos.CreateRoomRequest request) {
		User buyer = userRepository.findById(buyerId).orElseThrow(BusinessException::unauthenticated);
		SalePost post = salePostRepository.findByIdForUpdate(request.postId())
				.orElseThrow(BusinessException::notFound);
		if (post.isDeleted()) throw BusinessException.notFound();
		if (post.getSeller().getId().equals(buyerId)) {
			throw BusinessException.forbidden("본인의 판매글에는 채팅방을 만들 수 없습니다.");
		}

		return chatRoomRepository.findByPostIdAndBuyerId(post.getId(), buyerId)
				.map(room -> new ChatDtos.CreationResult<>(
						toRoom(room, buyerId, findLastMessage(room.getId())), false))
				.orElseGet(() -> {
					ChatRoom room = chatRoomRepository.saveAndFlush(new ChatRoom(post, buyer));
					return new ChatDtos.CreationResult<>(toRoom(room, buyerId, null), true);
				});
	}

	public PageResponse<ChatDtos.RoomResponse> listRooms(Long userId, String cursor, int limit) {
		CursorCodec.KeysetCursor decodedCursor = CursorCodec.decodeKeysetNullable(cursor);
		Instant cursorTime = decodedCursor == null ? null : decodedCursor.time();
		Long cursorId = decodedCursor == null ? null : decodedCursor.id();
		List<ChatRoom> chatRooms = chatRoomRepository.findPage(
				userId, cursorTime, cursorId, PageRequest.of(0, limit + 1));
		Map<Long, ChatMessage> lastMessagesByRoomId = loadLastMessages(chatRooms);

		return keysetPageFactory.create(
				chatRooms,
				limit,
				ChatRoom::getUpdatedAt,
				ChatRoom::getId,
				room -> toRoom(room, userId, lastMessagesByRoomId.get(room.getId())));
	}

	public ChatDtos.MessagePage listMessages(
			Long roomId, Long userId, String cursor, Long afterSequence, int limit) {
		requireParticipant(roomId, userId, false);
		if (cursor != null && afterSequence != null) {
			throw BusinessException.validation("cursor와 afterSequence는 함께 사용할 수 없습니다.");
		}
		return afterSequence == null
				? listHistory(roomId, cursor, limit)
				: listNewMessages(roomId, afterSequence, limit);
	}

	@Transactional
	public ChatDtos.CreationResult<ChatDtos.MessageResponse> sendMessage(
			Long roomId, Long senderId, ChatDtos.SendMessageRequest request) {
		ChatRoom room = requireParticipant(roomId, senderId, true);
		String clientMessageId = request.clientMessageId().toString();
		String content = request.content().trim();

		var existingMessage = chatMessageRepository.findByClientId(roomId, clientMessageId);
		if (existingMessage.isPresent()) {
			ChatMessage message = existingMessage.get();
			if (!message.hasSamePayload(senderId, content)) {
				throw new BusinessException(
						ErrorCode.IDEMPOTENCY_CONFLICT,
						"같은 clientMessageId가 다른 메시지에 사용되었습니다.");
			}
			return new ChatDtos.CreationResult<>(ChatDtos.MessageResponse.from(message), false);
		}

		User sender = userRepository.findById(senderId).orElseThrow(BusinessException::unauthenticated);
		Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		long sequence = room.nextSequence(sentAt);
		ChatMessage message = chatMessageRepository.saveAndFlush(
				new ChatMessage(room, sender, sequence, clientMessageId, content, sentAt));
		return new ChatDtos.CreationResult<>(ChatDtos.MessageResponse.from(message), true);
	}

	private ChatDtos.HistoryMessages listHistory(Long roomId, String cursor, int limit) {
		CursorCodec.KeysetCursor decodedCursor = CursorCodec.decodeKeysetNullable(cursor);
		Instant cursorTime = decodedCursor == null ? null : decodedCursor.time();
		Long cursorId = decodedCursor == null ? null : decodedCursor.id();
		List<ChatMessage> messages = chatMessageRepository.findHistory(
				roomId, cursorTime, cursorId, PageRequest.of(0, limit + 1));
		PageResponse<ChatDtos.MessageResponse> page = keysetPageFactory.create(
				messages, limit, ChatMessage::getCreatedAt, ChatMessage::getId, ChatDtos.MessageResponse::from);
		return new ChatDtos.HistoryMessages(page.items(), page.page());
	}

	private ChatDtos.NewMessages listNewMessages(Long roomId, long afterSequence, int limit) {
		if (afterSequence < 0) {
			throw BusinessException.validation("afterSequence는 0 이상이어야 합니다.");
		}
		List<ChatMessage> messages = new ArrayList<>(chatMessageRepository.findAfter(
				roomId, afterSequence, PageRequest.of(0, limit + 1)));
		boolean hasMore = messages.size() > limit;
		if (hasMore) messages.remove(messages.size() - 1);
		long nextSequence = messages.isEmpty() ? afterSequence : messages.get(messages.size() - 1).getSequence();
		return new ChatDtos.NewMessages(
				messages.stream().map(ChatDtos.MessageResponse::from).toList(), nextSequence, hasMore);
	}

	private ChatRoom requireParticipant(Long roomId, Long userId, boolean lock) {
		ChatRoom room = (lock
				? chatRoomRepository.findByIdForUpdate(roomId)
				: chatRoomRepository.findDetailedById(roomId))
				.orElseThrow(BusinessException::notFound);
		if (!room.hasParticipant(userId)) throw BusinessException.notFound();
		return room;
	}

	private ChatDtos.RoomResponse toRoom(ChatRoom room, Long viewerId, ChatMessage lastMessage) {
		User otherUser = room.otherParticipant(viewerId);
		SalePost post = room.getPost();
		ChatDtos.PostReference postReference = new ChatDtos.PostReference(
				post.getId().toString(), post.getTitle(), post.getStatus().name(), post.isDeleted());
		ChatDtos.MessageResponse lastMessageResponse = lastMessage == null
				? null : ChatDtos.MessageResponse.from(lastMessage);
		return new ChatDtos.RoomResponse(
				room.getId().toString(),
				postReference,
				PublicUserResponse.from(otherUser),
				lastMessageResponse,
				room.getCreatedAt(),
				room.getUpdatedAt());
	}

	private ChatMessage findLastMessage(Long roomId) {
		return chatMessageRepository.findFirstByRoomIdOrderBySequenceDesc(roomId).orElse(null);
	}

	private Map<Long, ChatMessage> loadLastMessages(List<ChatRoom> chatRooms) {
		List<Long> roomIds = chatRooms.stream().map(ChatRoom::getId).toList();
		if (roomIds.isEmpty()) return Map.of();
		return chatMessageRepository.findLastByRoomIds(roomIds).stream()
				.collect(Collectors.toMap(message -> message.getRoom().getId(), Function.identity()));
	}
}

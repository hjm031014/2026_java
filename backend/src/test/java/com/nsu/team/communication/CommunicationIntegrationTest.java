package com.nsu.team.communication;

import com.nsu.team.chat.*;
import com.nsu.team.comment.CommentDtos;
import com.nsu.team.comment.CommentRepository;
import com.nsu.team.comment.CommentService;
import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.favorite.FavoriteRepository;
import com.nsu.team.favorite.FavoriteService;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import com.nsu.team.domain.user.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CommunicationIntegrationTest {
    @Autowired CommentService commentService;
    @Autowired FavoriteService favoriteService;
    @Autowired ChatService chatService;
    @Autowired UserRepository users;
    @Autowired SalePostRepository posts;
    @Autowired CommentRepository comments;
    @Autowired FavoriteRepository favorites;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatMessageRepository messages;
    @Autowired EntityManager entityManager;

    User seller;
    User buyer;
    User outsider;
    SalePost post;

    @BeforeEach
    void setUp() {
        seller = users.save(newUser("seller@example.com", "seller"));
        buyer = users.save(newUser("buyer@example.com", "buyer"));
        outsider = users.save(newUser("outsider@example.com", "outsider"));
        post = posts.save(new SalePost(seller, null, null, "책 판매", "깨끗합니다", BigDecimal.valueOf(12000)));
        entityManager.flush();
    }

    private static User newUser(String email, String nickname) {
        return User.builder()
                .email(email)
                .passwordHash("hash")
                .nickname(nickname)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void commentCreationListingAndAuthorOnlySoftDeletion() {
        var created = commentService.create(
                post.getId(), buyer.getId(), new CommentDtos.CreateRequest(" 관심 있습니다 "));

        var page = commentService.list(post.getId(), null, 20);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).content()).isEqualTo("관심 있습니다");

        assertThatThrownBy(() -> commentService.delete(Long.valueOf(created.id()), seller.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        commentService.delete(Long.valueOf(created.id()), buyer.getId());
        assertThat(commentService.list(post.getId(), null, 20).items()).isEmpty();
        assertThat(comments.count()).isEqualTo(1);
    }

    @Test
    void favoriteAddAndRemoveAreIdempotent() {
        favoriteService.add(post.getId(), buyer.getId());
        favoriteService.add(post.getId(), buyer.getId());
        assertThat(favorites.count()).isEqualTo(1);

        favoriteService.add(post.getId(), outsider.getId());
        var favoritePage = favoriteService.list(buyer.getId(), null, 20);
        assertThat(favoritePage.items()).hasSize(1);
        assertThat(favoritePage.items().get(0).post().favoriteCount()).isEqualTo(2);

        favoriteService.remove(post.getId(), buyer.getId());
        favoriteService.remove(post.getId(), buyer.getId());
        assertThat(favorites.count()).isEqualTo(1);
    }

    @Test
    void commentCursorDoesNotDuplicateItems() {
        commentService.create(post.getId(), buyer.getId(), new CommentDtos.CreateRequest("첫 번째"));
        commentService.create(post.getId(), buyer.getId(), new CommentDtos.CreateRequest("두 번째"));
        commentService.create(post.getId(), buyer.getId(), new CommentDtos.CreateRequest("세 번째"));

        var first = commentService.list(post.getId(), null, 2);
        var second = commentService.list(post.getId(), first.page().nextCursor(), 2);

        assertThat(first.page().hasNext()).isTrue();
        assertThat(first.items()).hasSize(2);
        assertThat(second.page().hasNext()).isFalse();
        assertThat(second.items()).hasSize(1);
        assertThat(first.items()).extracting(CommentDtos.Response::id)
                .doesNotContain(second.items().get(0).id());
    }

    @Test
    void roomAndMessageRetriesReturnExistingResources() {
        var firstRoom = chatService.createRoom(buyer.getId(), new ChatDtos.CreateRoomRequest(post.getId()));
        var retriedRoom = chatService.createRoom(buyer.getId(), new ChatDtos.CreateRoomRequest(post.getId()));
        assertThat(firstRoom.created()).isTrue();
        assertThat(retriedRoom.created()).isFalse();
        assertThat(rooms.count()).isEqualTo(1);

        UUID clientId = UUID.randomUUID();
        var firstMessage = chatService.sendMessage(
                Long.valueOf(firstRoom.value().id()), buyer.getId(),
                new ChatDtos.SendMessageRequest(clientId, "안녕하세요"));
        var retriedMessage = chatService.sendMessage(
                Long.valueOf(firstRoom.value().id()), buyer.getId(),
                new ChatDtos.SendMessageRequest(clientId, "안녕하세요"));
        assertThat(firstMessage.created()).isTrue();
        assertThat(retriedMessage.created()).isFalse();
        assertThat(messages.count()).isEqualTo(1);
        assertThat(firstMessage.value().sequence()).isEqualTo(1);
        assertThat(chatService.listRooms(buyer.getId(), null, 20).items().get(0).lastMessage().content())
                .isEqualTo("안녕하세요");

        assertThatThrownBy(() -> chatService.sendMessage(
                Long.valueOf(firstRoom.value().id()), buyer.getId(),
                new ChatDtos.SendMessageRequest(clientId, "다른 내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void onlyParticipantsCanReadRoomAndSellerCannotOpenOwnRoom() {
        assertThatThrownBy(() -> chatService.createRoom(
                seller.getId(), new ChatDtos.CreateRoomRequest(post.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        var room = chatService.createRoom(buyer.getId(), new ChatDtos.CreateRoomRequest(post.getId()));

        assertThatThrownBy(() -> chatService.listMessages(
                Long.valueOf(room.value().id()), outsider.getId(), null, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void newMessagePollingKeepsRequestedSequenceForEmptyResult() {
        var room = chatService.createRoom(buyer.getId(), new ChatDtos.CreateRoomRequest(post.getId()));
        var data = (ChatDtos.NewMessages) chatService.listMessages(
                Long.valueOf(room.value().id()), buyer.getId(), null, 7L, 20);
        assertThat(data.items()).isEmpty();
        assertThat(data.nextAfterSequence()).isEqualTo(7);
        assertThat(data.hasMore()).isFalse();
    }

    @Test
    void cursorAndAfterSequenceCannotBeUsedTogether() {
        var room = chatService.createRoom(buyer.getId(), new ChatDtos.CreateRoomRequest(post.getId()));

        assertThatThrownBy(() -> chatService.listMessages(
                Long.valueOf(room.value().id()), buyer.getId(), "some-cursor", 1L, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void chatHistoryIsPreservedAfterPostIsDeletedButNewRoomsAreNotAllowed() {
        var room = chatService.createRoom(buyer.getId(), new ChatDtos.CreateRoomRequest(post.getId()));
        chatService.sendMessage(Long.valueOf(room.value().id()), buyer.getId(),
                new ChatDtos.SendMessageRequest(UUID.randomUUID(), "삭제 전 메시지"));

        post.delete();
        posts.saveAndFlush(post);

        var history = chatService.listMessages(
                Long.valueOf(room.value().id()), buyer.getId(), null, null, 20);
        assertThat(((ChatDtos.HistoryMessages) history).items()).hasSize(1);
        assertThat(chatService.listRooms(buyer.getId(), null, 20).items()).hasSize(1);

        assertThatThrownBy(() -> chatService.createRoom(
                outsider.getId(), new ChatDtos.CreateRoomRequest(post.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

}

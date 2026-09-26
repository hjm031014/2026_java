package com.nsu.team.communication;

import com.nsu.team.chat.*;
import com.nsu.team.comment.CommentDtos;
import com.nsu.team.comment.CommentRepository;
import com.nsu.team.comment.CommentService;
import com.nsu.team.common.ApiException;
import com.nsu.team.favorite.FavoriteRepository;
import com.nsu.team.favorite.FavoriteService;
import com.nsu.team.post.SalePost;
import com.nsu.team.post.SalePostRepository;
import com.nsu.team.user.UserAccount;
import com.nsu.team.user.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CommunicationIntegrationTest {
    @Autowired CommentService commentService;
    @Autowired FavoriteService favoriteService;
    @Autowired ChatService chatService;
    @Autowired UserAccountRepository users;
    @Autowired SalePostRepository posts;
    @Autowired CommentRepository comments;
    @Autowired FavoriteRepository favorites;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatMessageRepository messages;
    @Autowired EntityManager entityManager;

    UserAccount seller;
    UserAccount buyer;
    UserAccount outsider;
    SalePost post;

    @BeforeEach
    void setUp() {
        seller = users.save(new UserAccount("seller@example.com", "hash", "seller"));
        buyer = users.save(new UserAccount("buyer@example.com", "hash", "buyer"));
        outsider = users.save(new UserAccount("outsider@example.com", "hash", "outsider"));
        post = posts.save(new SalePost(seller, null, null, "책 판매", "깨끗합니다", BigDecimal.valueOf(12000)));
        entityManager.flush();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void commentCreationListingAndAuthorOnlySoftDeletion() {
        authenticate(buyer);
        var created = commentService.create(post.getId(), new CommentDtos.CreateRequest(" 관심 있습니다 "));

        SecurityContextHolder.clearContext();
        var page = commentService.list(post.getId(), null, 20);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).content()).isEqualTo("관심 있습니다");

        authenticate(seller);
        assertThatThrownBy(() -> commentService.delete(Long.valueOf(created.id())))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("FORBIDDEN");

        authenticate(buyer);
        commentService.delete(Long.valueOf(created.id()));
        assertThat(commentService.list(post.getId(), null, 20).items()).isEmpty();
        assertThat(comments.count()).isEqualTo(1);
    }

    @Test
    void favoriteAddAndRemoveAreIdempotent() {
        authenticate(buyer);
        favoriteService.add(post.getId());
        favoriteService.add(post.getId());
        assertThat(favorites.count()).isEqualTo(1);

        authenticate(outsider);
        favoriteService.add(post.getId());
        authenticate(buyer);
        var favoritePage = favoriteService.list(null, 20);
        assertThat(favoritePage.items()).hasSize(1);
        assertThat(favoritePage.items().get(0).post().favoriteCount()).isEqualTo(2);

        favoriteService.remove(post.getId());
        favoriteService.remove(post.getId());
        assertThat(favorites.count()).isEqualTo(1);
    }

    @Test
    void commentCursorDoesNotDuplicateItems() {
        authenticate(buyer);
        commentService.create(post.getId(), new CommentDtos.CreateRequest("첫 번째"));
        commentService.create(post.getId(), new CommentDtos.CreateRequest("두 번째"));
        commentService.create(post.getId(), new CommentDtos.CreateRequest("세 번째"));

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
        authenticate(buyer);
        var firstRoom = chatService.createRoom(new ChatDtos.CreateRoomRequest(post.getId()));
        var retriedRoom = chatService.createRoom(new ChatDtos.CreateRoomRequest(post.getId()));
        assertThat(firstRoom.created()).isTrue();
        assertThat(retriedRoom.created()).isFalse();
        assertThat(rooms.count()).isEqualTo(1);

        UUID clientId = UUID.randomUUID();
        var firstMessage = chatService.sendMessage(
                Long.valueOf(firstRoom.value().id()), new ChatDtos.SendMessageRequest(clientId, "안녕하세요"));
        var retriedMessage = chatService.sendMessage(
                Long.valueOf(firstRoom.value().id()), new ChatDtos.SendMessageRequest(clientId, "안녕하세요"));
        assertThat(firstMessage.created()).isTrue();
        assertThat(retriedMessage.created()).isFalse();
        assertThat(messages.count()).isEqualTo(1);
        assertThat(firstMessage.value().sequence()).isEqualTo(1);
        assertThat(chatService.listRooms(null, 20).items().get(0).lastMessage().content())
                .isEqualTo("안녕하세요");

        assertThatThrownBy(() -> chatService.sendMessage(
                Long.valueOf(firstRoom.value().id()), new ChatDtos.SendMessageRequest(clientId, "다른 내용")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void onlyParticipantsCanReadRoomAndSellerCannotOpenOwnRoom() {
        authenticate(seller);
        assertThatThrownBy(() -> chatService.createRoom(new ChatDtos.CreateRoomRequest(post.getId())))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("FORBIDDEN");

        authenticate(buyer);
        var room = chatService.createRoom(new ChatDtos.CreateRoomRequest(post.getId()));

        authenticate(outsider);
        assertThatThrownBy(() -> chatService.listMessages(Long.valueOf(room.value().id()), null, null, 20))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void newMessagePollingKeepsRequestedSequenceForEmptyResult() {
        authenticate(buyer);
        var room = chatService.createRoom(new ChatDtos.CreateRoomRequest(post.getId()));
        var data = (ChatDtos.NewMessages) chatService.listMessages(
                Long.valueOf(room.value().id()), null, 7L, 20);
        assertThat(data.items()).isEmpty();
        assertThat(data.nextAfterSequence()).isEqualTo(7);
        assertThat(data.hasMore()).isFalse();
    }

    private void authenticate(UserAccount user) {
        var authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

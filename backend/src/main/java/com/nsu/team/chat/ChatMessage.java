package com.nsu.team.chat;

import com.nsu.team.user.UserAccount;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "chat_messages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_messages_room_sequence", columnNames = {"room_id", "sequence"}),
                @UniqueConstraint(name = "uk_chat_messages_room_client", columnNames = {"room_id", "client_message_id"})
        },
        indexes = @Index(name = "idx_chat_messages_room_created", columnList = "room_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private UserAccount sender;
    @Column(nullable = false)
    private long sequence;
    @Column(name = "client_message_id", nullable = false, length = 100)
    private String clientMessageId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ChatMessage(ChatRoom room, UserAccount sender, long sequence,
                       String clientMessageId, String content, Instant createdAt) {
        this.room = room;
        this.sender = sender;
        this.sequence = sequence;
        this.clientMessageId = clientMessageId;
        this.content = content;
        this.createdAt = createdAt;
    }
}

package com.nsu.team.chat;

import com.nsu.team.post.SalePost;
import com.nsu.team.user.UserAccount;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_rooms_post_buyer", columnNames = {"post_id", "buyer_id"}),
        indexes = {
                @Index(name = "idx_chat_rooms_seller_updated", columnList = "seller_id, updated_at"),
                @Index(name = "idx_chat_rooms_buyer_updated", columnList = "buyer_id, updated_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private SalePost post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private UserAccount seller;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private UserAccount buyer;
    @Column(name = "last_message_at")
    private Instant lastMessageAt;
    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ChatRoom(SalePost post, UserAccount buyer) {
        this.post = post;
        this.seller = post.getSeller();
        this.buyer = buyer;
    }

    public long nextSequence(Instant sentAt) {
        lastSequence += 1;
        lastMessageAt = sentAt;
        updatedAt = sentAt;
        return lastSequence;
    }

    @PrePersist void created() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }
}

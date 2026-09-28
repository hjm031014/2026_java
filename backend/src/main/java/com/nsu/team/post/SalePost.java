package com.nsu.team.post;

import com.nsu.team.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "sale_posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalePost {
    public enum Status { SELLING, RESERVED, SOLD, DELETED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meetup_location_id")
    private MeetupLocation meetupLocation;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.SELLING;
    @Column(name = "view_count", nullable = false)
    private long viewCount;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SalePost(User seller, Category category, MeetupLocation meetupLocation,
                    String title, String description, BigDecimal price) {
        this.seller = seller;
        this.category = category;
        this.meetupLocation = meetupLocation;
        this.title = title;
        this.description = description;
        this.price = price;
    }

    public boolean isDeleted() { return status == Status.DELETED; }

    @PrePersist void created() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}

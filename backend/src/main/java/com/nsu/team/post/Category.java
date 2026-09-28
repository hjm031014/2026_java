package com.nsu.team.post;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id")
    private Category parent;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public Category(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }
}

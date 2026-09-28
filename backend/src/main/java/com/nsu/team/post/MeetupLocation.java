package com.nsu.team.post;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "meetup_locations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetupLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Column(length = 255)
    private String description;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public MeetupLocation(String name, String description) {
        this.name = name;
        this.description = description;
    }
}

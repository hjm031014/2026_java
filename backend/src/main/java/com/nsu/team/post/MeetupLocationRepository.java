package com.nsu.team.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetupLocationRepository extends JpaRepository<MeetupLocation, Long> {
    List<MeetupLocation> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

    boolean existsByName(String name);
}

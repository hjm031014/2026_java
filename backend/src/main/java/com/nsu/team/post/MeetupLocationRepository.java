package com.nsu.team.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetupLocationRepository extends JpaRepository<MeetupLocation, Long> {
    List<MeetupLocation> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
}

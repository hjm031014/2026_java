package com.nsu.team.post;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final CategoryRepository categoryRepository;
    private final MeetupLocationRepository meetupLocationRepository;

    public List<PostDtos.CategoryResponse> categories() {
        return categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(PostDtos.CategoryResponse::from)
                .toList();
    }

    public List<PostDtos.TradePlaceResponse> tradePlaces() {
        return meetupLocationRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(PostDtos.TradePlaceResponse::from)
                .toList();
    }
}

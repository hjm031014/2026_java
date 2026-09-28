package com.nsu.team.post;

import com.nsu.team.common.response.ApiResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReferenceController {

    private final CategoryRepository categoryRepository;
    private final MeetupLocationRepository meetupLocationRepository;

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, List<PostDtos.CategoryResponse>>> categories() {
        return ApiResponse.of(Map.of("items", categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(PostDtos.CategoryResponse::from).toList()));
    }

    @GetMapping("/trade-places")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, List<PostDtos.TradePlaceResponse>>> tradePlaces() {
        return ApiResponse.of(Map.of("items", meetupLocationRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc()
                .stream().map(PostDtos.TradePlaceResponse::from).toList()));
    }
}

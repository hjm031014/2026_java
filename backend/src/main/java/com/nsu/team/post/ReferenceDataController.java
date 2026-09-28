package com.nsu.team.post;

import com.nsu.team.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    @GetMapping("/categories")
    public ApiResponse<Items<PostDtos.CategoryResponse>> categories() {
        return ApiResponse.of(new Items<>(referenceDataService.categories()));
    }

    @GetMapping("/trade-places")
    public ApiResponse<Items<PostDtos.TradePlaceResponse>> tradePlaces() {
        return ApiResponse.of(new Items<>(referenceDataService.tradePlaces()));
    }

    public record Items<T>(List<T> items) {
    }
}

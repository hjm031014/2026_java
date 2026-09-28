package com.nsu.team.post;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리·거래 장소 기본 데이터를 없는 항목만 채워 넣는다(멱등). */
@Component
@ConditionalOnProperty(name = "app.seed.reference-data", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReferenceDataSeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final MeetupLocationRepository meetupLocationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String[] categories = {"도서", "전자기기", "의류·잡화", "생활·가전", "가구", "기타"};
        for (int i = 0; i < categories.length; i++) {
            if (!categoryRepository.existsByName(categories[i])) {
                categoryRepository.save(new Category(categories[i], i + 1));
            }
        }
        String[][] places = {
                {"도서관 앞", "중앙도서관 정문 앞"},
                {"학생회관", "학생회관 1층 로비"},
                {"정문", "학교 정문 앞"},
                {"기숙사", "기숙사 1층 로비"},
                {"학생식당", "학생식당 입구"},
        };
        for (int i = 0; i < places.length; i++) {
            if (!meetupLocationRepository.existsByName(places[i][0])) {
                meetupLocationRepository.save(new MeetupLocation(places[i][0], places[i][1], i + 1));
            }
        }
    }
}

package com.ssafy.search.keywordRanking.processor;

import com.ssafy.search.keywordRanking.dto.KeywordDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@StepScope // 💡 Reader를 매 Step마다 새로 생성하도록 설정
public class KeywordRankReader implements ItemReader<KeywordDto> {

    private final ZSetOperations<String, String> zSetOperations;

    private ListItemReader<KeywordDto> delegate;

    @Override
    public KeywordDto read() {
        if (delegate == null) {
            LocalDateTime timeBlock = getTargetTimeBlock();
            String redisKey = getTimeBlockKey(timeBlock);
            log.info("읽는 Redis 키 (1시간 단위): {}", redisKey);

            Set<ZSetOperations.TypedTuple<String>> zset = zSetOperations.reverseRangeWithScores(redisKey, 0, 9);
            List<KeywordDto> keywords = new ArrayList<>();
            int rank = 1;
            if (zset != null) {
                for (ZSetOperations.TypedTuple<String> tuple : zset) {
                    keywords.add(new KeywordDto(
                            tuple.getValue(),
                            rank++,
                            tuple.getScore() != null ? tuple.getScore() : 0.0
                    ));
                }
            }

            log.info("키워드 개수 (1시간 단위): {}", keywords.size());
            delegate = new ListItemReader<>(keywords);
        }

        return delegate.read(); // 이게 핵심: 내부 Reader에서 하나씩 꺼냄
    }

    private LocalDateTime getTargetTimeBlock() {
        LocalDateTime now = LocalDateTime.now().minusHours(1);
        return now.withSecond(0).withNano(0).withMinute(0);
    }

    private String getTimeBlockKey(LocalDateTime time) {
        String date = time.format(DateTimeFormatter.ofPattern("yyyyMMdd_HH"));
        return "popular_keywords:" + date + ":00";
    }
}

package com.ssafy.search.keywordRanking.processor;

import com.ssafy.search.keywordRanking.dto.KeywordRankDto;
import com.ssafy.search.keywordRanking.mapper.KeywordRankMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeywordRankWriter implements ItemWriter<KeywordRankDto> {

    private final KeywordRankMapper keywordRankMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void write(Chunk<? extends KeywordRankDto> chunk) {
        LocalDateTime timeBlock = getTargetTimeBlock();
        List<? extends KeywordRankDto> items = chunk.getItems();

        if (items.isEmpty()) {
            // ✅ 아무 키워드가 없어도 빈 row insert
            KeywordRankDto empty = new KeywordRankDto(
                    timeBlock,
                    "-", // placeholder
                    0,
                    "NONE"
            );
            keywordRankMapper.insertKeywordRanks(Collections.singletonList(empty));
            log.info("검색어 없음 - 빈 블록 저장 완료 (1시간 단위): {}", timeBlock);
        } else {
            keywordRankMapper.insertKeywordRanks((List<KeywordRankDto>) items);
            log.info("집계 완료 - 키워드 {}개 저장 (1시간 단위)", items.size());
        }

        // ✅ Redis 키 삭제
        String redisKey = getTimeBlockKey(timeBlock);
        redisTemplate.delete(redisKey);
        log.info("삭제된 Redis 키 (1시간 단위): {}", redisKey);
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
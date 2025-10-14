package com.ssafy.search.keywordRanking.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.ssafy.search.keywordRanking.dto.KeywordRankDto;
import com.ssafy.search.keywordRanking.mapper.KeywordRankMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KeywordRankServiceImpl implements KeywordRankService {

    private final ZSetOperations<String, String> zSetOperations;
    private final RedisTemplate<String, String> redisTemplate;
    private final KeywordRankMapper keywordRankMapper;

    public void increaseKeywordCount(String keyword) {
        String redisKey = getTimeBlockKey(LocalDateTime.now());
        zSetOperations.incrementScore(redisKey, keyword, 1);
        redisTemplate.expire(redisKey, Duration.ofHours(2)); // 1시간 단위로 변경하고 TTL도 2시간으로 설정
    }

    public String getTimeBlockKey(LocalDateTime time) {
        String date = time.format(DateTimeFormatter.ofPattern("yyyyMMdd_HH"));
        return "popular_keywords:" + date + ":00";
    }

    @Override
    public List<KeywordRankDto> getLatestRankings() {
        LocalDateTime latestTimeBlock = keywordRankMapper.findLatestTimeBlock();
        return keywordRankMapper.selectRanksByTime(latestTimeBlock);
    }
}
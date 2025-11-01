package com.ssafy.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexInitializer {

    private final ElasticsearchClient elasticsearchClient;

    @Bean
    public ApplicationRunner initializeElasticsearchIndices() {
        return args -> {
            try {
                createIndexIfNotExists("products", getProductsMapping());
                createIndexIfNotExists("feeds", getFeedsMapping());
                createIndexIfNotExists("search_suggest", getSearchSuggestMapping());
                createIndexIfNotExists("users", getUsersMapping());
                log.info("Elasticsearch 인덱스 초기화 완료");
            } catch (Exception e) {
                log.error("Elasticsearch 인덱스 초기화 실패", e);
            }
        };
    }

    private void createIndexIfNotExists(String indexName, Map<String, Property> mappings) throws IOException {
        boolean exists = elasticsearchClient.indices()
                .exists(ExistsRequest.of(e -> e.index(indexName)))
                .value();

        if (exists) {
            // try {
            //     // 기존 매핑 검증 시도
            //     validateExistingMapping(indexName, mappings);
            //     log.info("인덱스 '{}' 기존 매핑 호환 - 보존", indexName);
            //     return;
            // } catch (Exception e) {
            //     // 매핑 충돌 발생 시 재생성
            //     log.warn("인덱스 '{}' 매핑 충돌 감지 - 재생성 필요: {}", indexName, e.getMessage());
            //     elasticsearchClient.indices().delete(d -> d.index(indexName));
            //     log.info("기존 인덱스 '{}' 삭제 완료", indexName);
            // }
            elasticsearchClient.indices().delete(d -> d.index(indexName));
            log.info("기존 인덱스 '{}' 삭제 완료", indexName);
        }
        
        // elasticsearch-settings.json 파일 읽기
        String settings = loadElasticsearchSettings();
        
        CreateIndexRequest request = CreateIndexRequest.of(c -> c
                .index(indexName)
                .settings(s -> s.withJson(new java.io.StringReader(settings)))
                .mappings(m -> m.properties(mappings))
        );

        elasticsearchClient.indices().create(request);
        log.info("인덱스 '{}' 생성 완료", indexName);
    }

    private String loadElasticsearchSettings() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("elasticsearch-settings.json")) {
            if (is == null) {
                throw new RuntimeException("elasticsearch-settings.json 파일을 찾을 수 없습니다");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("elasticsearch-settings.json 로드 실패", e);
            return "{}"; // 기본 설정
        }
    }

    private void validateExistingMapping(String indexName, Map<String, Property> expectedMappings) throws IOException {
        try {
            // 기존 매핑 조회
            var response = elasticsearchClient.indices().getMapping(g -> g.index(indexName));
            var existingMapping = response.get(indexName);
            
            if (existingMapping == null) {
                throw new RuntimeException("매핑 정보를 가져올 수 없습니다");
            }
            
            // 간단한 호환성 검사 (필요한 필드가 있는지만 확인)
            var existingProperties = existingMapping.mappings().properties();
            for (String fieldName : expectedMappings.keySet()) {
                if (!existingProperties.containsKey(fieldName)) {
                    throw new RuntimeException("필수 필드 누락: " + fieldName);
                }
            }
            
            log.debug("인덱스 '{}' 매핑 검증 통과", indexName);
        } catch (Exception e) {
            throw new IOException("매핑 검증 실패: " + e.getMessage(), e);
        }
    }

    private Map<String, Property> getProductsMapping() {
        return Map.of(
                "productId", Property.of(p -> p.keyword(k -> k)),
                "productName", Property.of(p -> p.text(t -> t
                        .analyzer("jaso_index_analyzer")
                        .searchAnalyzer("jaso_search_analyzer")
                        .fields(Map.of(
                                "jaso", Property.of(f -> f.text(ft -> ft
                                        .analyzer("jaso_index_analyzer")
                                        .searchAnalyzer("jaso_search_analyzer"))),
                                "standard_en", Property.of(f -> f.text(ft -> ft
                                        .analyzer("standard_en_analyzer"))),
                                "ngram_en", Property.of(f -> f.text(ft -> ft
                                        .analyzer("ngram_en_analyzer")))
                        )))),
                "categoryId", Property.of(p -> p.keyword(k -> k)),
                "categoryName", Property.of(p -> p.text(t -> t
                        .analyzer("jaso_index_analyzer")
                        .searchAnalyzer("jaso_search_analyzer"))),
                "price", Property.of(p -> p.integer(i -> i)),
                "imageUrl", Property.of(p -> p.keyword(k -> k)),
                "manufacturer", Property.of(p -> p.text(t -> t
                        .analyzer("jaso_index_analyzer")
                        .searchAnalyzer("jaso_search_analyzer")))
        );
    }

    private Map<String, Property> getFeedsMapping() {
        return Map.of(
                "feedId", Property.of(p -> p.keyword(k -> k)),
                "content", Property.of(p -> p.text(t -> t.analyzer("jaso_index_analyzer").searchAnalyzer("jaso_search_analyzer"))),
                "hashtags", Property.of(p -> p.nested(n -> n.properties(Map.of(
                        "name", Property.of(pr -> pr.text(t -> t.analyzer("jaso_index_analyzer").searchAnalyzer("jaso_search_analyzer"))),
                        "category", Property.of(pr -> pr.keyword(k -> k))
                )))),
                "taggedProducts", Property.of(p -> p.nested(n -> n.properties(Map.of(
                        "productId", Property.of(pr -> pr.keyword(k -> k)),
                        "productName", Property.of(pr -> pr.text(t -> t.analyzer("jaso_index_analyzer").searchAnalyzer("jaso_search_analyzer")))
                )))),
                "createdAt", Property.of(p -> p.date(d -> d)),
                "thumbnailUrl", Property.of(p -> p.keyword(k -> k))
        );
    }

    private Map<String, Property> getSearchSuggestMapping() {
        return Map.of(
                "name", Property.of(p -> p.text(t -> t
                        .analyzer("jaso_index_analyzer")
                        .searchAnalyzer("jaso_search_analyzer")
                        .fields("keyword", Property.of(f -> f.keyword(k -> k))))),
                "searchCount", Property.of(p -> p.integer(i -> i)),
                "isAd", Property.of(p -> p.boolean_(b -> b)),
                "adScore", Property.of(p -> p.float_(f -> f))
        );
    }

    private Map<String, Property> getUsersMapping() {
        return Map.of(
                "userId", Property.of(p -> p.integer(i -> i)),
                "nickname", Property.of(p -> p.text(t -> t
                        .analyzer("jaso_index_analyzer")
                        .searchAnalyzer("jaso_search_analyzer")
                        .fields(Map.of(
                                "jaso", Property.of(f -> f.text(ft -> ft
                                        .analyzer("jaso_index_analyzer")
                                        .searchAnalyzer("jaso_search_analyzer"))),
                                "standard_en", Property.of(f -> f.text(ft -> ft
                                        .analyzer("standard_en_analyzer"))),
                                "ngram_en", Property.of(f -> f.text(ft -> ft
                                        .analyzer("ngram_en_analyzer")))
                        )))),
                "profileContent", Property.of(p -> p.text(t -> t
                        .analyzer("jaso_index_analyzer")
                        .searchAnalyzer("jaso_search_analyzer"))),
                "brix", Property.of(p -> p.integer(i -> i)),
                "profileImageUrl", Property.of(p -> p.keyword(k -> k))
        );
    }
}
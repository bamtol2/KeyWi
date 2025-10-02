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

        if (!exists) {
            // elasticsearch-settings.json 파일 읽기
            String settings = loadElasticsearchSettings();
            
            CreateIndexRequest request = CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .settings(s -> s.withJson(new java.io.StringReader(settings)))
                    .mappings(m -> m.properties(mappings))
            );

            elasticsearchClient.indices().create(request);
            log.info("인덱스 '{}' 생성 완료", indexName);
        } else {
            log.info("인덱스 '{}'는 이미 존재합니다", indexName);
        }
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

    private Map<String, Property> getProductsMapping() {
        return Map.of(
                "productId", Property.of(p -> p.keyword(k -> k)),
                "productName", Property.of(p -> p.text(t -> t.analyzer("jaso_index_analyzer").searchAnalyzer("jaso_search_analyzer"))),
                "categoryId", Property.of(p -> p.keyword(k -> k)),
                "categoryName", Property.of(p -> p.text(t -> t.analyzer("jaso_index_analyzer").searchAnalyzer("jaso_search_analyzer"))),
                "price", Property.of(p -> p.integer(i -> i)),
                "imageUrl", Property.of(p -> p.keyword(k -> k)),
                "manufacturer", Property.of(p -> p.text(t -> t.analyzer("jaso_index_analyzer").searchAnalyzer("jaso_search_analyzer")))
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
                        .analyzer("suggest_index_analyzer")
                        .searchAnalyzer("suggest_search_analyzer")
                        .fields("keyword", Property.of(f -> f.keyword(k -> k))))),
                "searchCount", Property.of(p -> p.integer(i -> i)),
                "isAd", Property.of(p -> p.boolean_(b -> b)),
                "adScore", Property.of(p -> p.float_(f -> f))
        );
    }

    private Map<String, Property> getUsersMapping() {
        return Map.of(
                "userId", Property.of(p -> p.keyword(k -> k)),
                "userName", Property.of(p -> p.text(t -> t.analyzer("jaso_index_analyzer").searchAnalyzer("jaso_search_analyzer"))),
                "userNickname", Property.of(p -> p.text(t -> t.analyzer("jaso_index_analyzer").searchAnalyzer("jaso_search_analyzer"))),
                "brix", Property.of(p -> p.integer(i -> i))
        );
    }
}
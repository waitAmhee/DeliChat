package com.AIstudy.delichat.rag.service;


import com.AIstudy.delichat.rag.dto.FaqContextResult;
import com.AIstudy.delichat.rag.repository.FaqRepository;
import com.AIstudy.delichat.rag.dto.FaqSimilarResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqSearchService {

    private static final double SIMILARITY_THRESHOLD= 0.5;
    private static final int TOP_K=3;

    // 자주 반복되는 질문은 임베딩/pgvector 재호출 없이 바로 서빙하기 위한 캐시 설정
    private static final String CACHE_KEY_PREFIX = "faq:cache:answer:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final FaqRepository faqRepository;
    private final EmbeddingService embeddingService;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final QueryNormalizationService queryNormalizationService;

    /**
     * 사용자 질문에 대한 FAQ 근거자료를 조회한다. KOMORAN으로 조사/어미를 제거해 정규화한 질문 문자열로
     * Redis 캐시를 먼저 확인하고, 캐시 미스일 때만 임베딩 + pgvector 유사도 검색을 수행한다.
     */
    public FaqContextResult buildContextFor(String userQuestion){
        // 1. 정규화된 질문으로 캐시를 먼저 조회 - 히트하면 임베딩/pgvector 호출을 완전히 스킵
        String cacheKey = CACHE_KEY_PREFIX + queryNormalizationService.normalize(userQuestion);
        FaqContextResult cached = readFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 사용자 질문 임베딩화
        float[] queryVector = embeddingService.embed(userQuestion);

        // 3. 사용자 질문과 유사한 3개 문서 select
        List<FaqSimilarResult> results = faqRepository.searchSimilarFaqs(queryVector,TOP_K);

        // 4. 유사도가 0.5 이상인 문서들로만 구성
        List<FaqSimilarResult> relevant = results.stream()
                .filter(r-> r.similarity()>=SIMILARITY_THRESHOLD)
                .toList();

        // 5. 없으면 context 전달 X (못 찾은 결과는 캐싱하지 않음 - 새 FAQ가 추가돼도 바로 반영되도록)
        if(relevant.isEmpty()){
            return new FaqContextResult("",false,null,null);
        }

        // 6. relevant는 similarity DESC로 정렬돼 있으므로 첫 번째가 가장 유사한 FAQ
        FaqSimilarResult top = relevant.get(0);
        FaqContextResult result = new FaqContextResult(buildContext(relevant),true,top.id(),top.similarity());

        // 7. 매칭에 성공한 결과만 캐시에 적재
        writeToCache(cacheKey, result);
        return result;
    }

    private FaqContextResult readFromCache(String cacheKey){
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            return cachedJson == null ? null : jsonMapper.readValue(cachedJson, FaqContextResult.class);
        } catch (Exception e) {
            log.warn("FAQ 캐시 조회 실패, 정상 검색으로 폴백합니다. key={}", cacheKey, e);
            return null;
        }
    }

    private void writeToCache(String cacheKey, FaqContextResult result){
        try {
            String json = jsonMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("FAQ 캐시 저장 실패, 검색 결과는 정상 반환합니다. key={}", cacheKey, e);
        }
    }

    private String buildContext(List<FaqSimilarResult> results){
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<results.size();i++){
            FaqSimilarResult r = results.get(i);
            sb.append("[참고자료 ").append(i+1).append("]\n")
                    .append("Q: ").append(r.question()).append("\n")
                    .append("A: ").append(r.answer()).append("\n\n");
        }
        return sb.toString();
    }

}

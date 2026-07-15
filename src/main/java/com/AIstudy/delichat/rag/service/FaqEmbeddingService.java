package com.AIstudy.delichat.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FaqEmbeddingService {

    private final JdbcTemplate jdbcTemplate;

    // Spring AI를 통해 임베딩 모델에게 토큰화 + 벡터 변환을 요청하는 추상화 계층.
    private final EmbeddingService embeddingService;

    public int embeddingMissingFaqs() {
        // 1. PostgreSQL에 저장한 KB 문서 추출
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, question, answer FROM cs_faq WHERE embedding IS NULL"
        );

        // 2. 질문-답변 쌍을 꺼냄
        for (Map<String, Object> row : rows) {
            Long id = (Long) row.get("id");
            String text = row.get("question") + " " + row.get("answer");

            // 3. 질문-답변을 벡터형태 -> string 형태로 변환
            float[] vector = embeddingService.embed(text);
            String literal = embeddingService.toVectorLiteral(vector);

            // 4. 변환한 형태를 저장
            jdbcTemplate.update(
                    "UPDATE cs_faq SET embedding =?::vector WHERE id=?",
                    literal, id
            );

        }
            return rows.size();

    }
}

package com.AIstudy.delichat.rag.repository;

import com.AIstudy.delichat.rag.dto.FaqSimilarResult;
import com.AIstudy.delichat.rag.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FaqRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    // 유사도 검색 기능
    public List<FaqSimilarResult> searchSimilarFaqs(float[] queryVector, int topK) {
        // 1. 질문 벡터와 저장된 FAQ 벡터 간 코사인 유사도를 계산해서
        //    가장 유사한 topK개만 뽑아오는 쿼리
        // 거리는 작을 수록 비슷함 (0~2)
        String sql = """
                SELECT id, category, question, answer,
                1-(embedding <=> ?::vector) AS similarity
                FROM cs_faq
                WHERE embedding IS NOT NULL
                ORDER BY similarity DESC
                LIMIT ?
                """;

        // 2. 질문을 String 형태로 변환
        String literal = embeddingService.toVectorLiteral(queryVector);

        // 3. 질문-문서 간의 유사도 검색
        return jdbcTemplate.query(sql, (rs, rowNum) -> new FaqSimilarResult(
                rs.getLong("id"),
                rs.getString("category"),
                rs.getString("question"),
                rs.getString("answer"),
                rs.getDouble("similarity")
        ), literal, topK);
    }
}

package com.AIstudy.delichat.rag.repository;

import com.AIstudy.delichat.rag.dto.FaqQuestionAnswer;
import com.AIstudy.delichat.rag.dto.FaqSimilarResult;
import com.AIstudy.delichat.rag.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    // 임베딩이 비어있는 FAQ id 전체 조회 (백필 대상 목록).
    public List<Long> findIdsMissingEmbedding() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM cs_faq WHERE embedding IS NULL", Long.class
        );
    }

    // FOR UPDATE SKIP LOCKED로 행을 클레임해서 값을 가져오는 메서드
    public Optional<FaqQuestionAnswer> claimForEmbedding(Long id) {
        String sql = """
                SELECT question, answer
                FROM cs_faq
                WHERE id = ? AND embedding IS NULL
                FOR UPDATE SKIP LOCKED
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new FaqQuestionAnswer(
                rs.getString("question"),
                rs.getString("answer")
        ), id).stream().findFirst();
    }

    // 문서 cs_faq 업데이트 메서드
    public void updateEmbedding(Long id, String vectorLiteral) {
        jdbcTemplate.update(
                "UPDATE cs_faq SET embedding = ?::vector WHERE id = ?",
                vectorLiteral, id
        );
    }
}

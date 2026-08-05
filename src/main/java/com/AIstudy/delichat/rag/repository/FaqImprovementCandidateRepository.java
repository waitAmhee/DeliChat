package com.AIstudy.delichat.rag.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// KAN-7: FAQ 개선 후보 추출 컨슈머가 채점 결과를 남기는 저장소.
@Repository
@RequiredArgsConstructor
public class FaqImprovementCandidateRepository {

    private final JdbcTemplate jdbcTemplate;

    // outbox_id UNIQUE 제약 + ON CONFLICT DO NOTHING으로 Kafka 재전달(at-least-once) 시 중복 저장을 막는다.
    // 재전달마다 outbox_id로 다시 저장 시도하면 유니크 키 제약 조건 위반으로 에러 발생(try-catch 문 필요)
    // CONFLICT DO NOTHING -> INSERT 시 지정한 제약 조건과 충돌하는 행이 이미 존재하면 에러를 던지지 않고 넘어가는 쿼리 -> 따라서 별도 예외 처리 로직 없이 멱등성 보장 가능
    public void save(Long outboxId, Integer faithfulnessScore, String failureType, String reason){
        String sql = """
                INSERT INTO faq_improvement_candidate (outbox_id, faithfulness_score, failure_type, reason)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (outbox_id) DO NOTHING
                """;

        jdbcTemplate.update(sql, outboxId, faithfulnessScore, failureType, reason);
    }
}

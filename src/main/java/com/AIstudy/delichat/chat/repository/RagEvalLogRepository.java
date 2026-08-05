package com.AIstudy.delichat.chat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RagEvalLogRepository {

    private final JdbcTemplate jdbcTemplate;

    // 생성된 id를 반환  outbox 이벤트가 이 id를 실어서 judge 컨슈머가 rag_eval_judgement 토픽과 연결
    public Long save(Long sessionId, String question, String context, String answer){
        String sql = """
                INSERT INTO rag_eval_log (session_id, question, context, answer)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """;

        return jdbcTemplate.queryForObject(sql, Long.class, sessionId, question, context, answer);
    }

}

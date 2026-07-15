package com.AIstudy.delichat.chat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RagEvalLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public void save(Long sessionId, String question, String context, String answer){
        String sql = """
                INSERT INTO rag_eval_log (session_id, question, context, answer)
                VALUES (?, ?, ?, ?)
                """;

        jdbcTemplate.update(sql, sessionId, question, context, answer);
    }

}

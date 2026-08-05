package com.AIstudy.delichat.chat.repository;

import com.AIstudy.delichat.chat.dto.RagEvalOutboxRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

//채팅 답변 완료 이벤트를 outbox에 남긴다. found 여부와 무관하게 항상 저장되고,
// outbox relay가 이 테이블을 읽어 Kafka로 발행한다.
@Repository
@RequiredArgsConstructor
public class RagEvalOutboxRepository {

    // relay가 백필 시 한 번에 처리할 최대 건수
    private static final int RELAY_BACKFILL_BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;

    public void save(Long sessionId, String question, String context, String answer,
                      boolean found, Long evalLogId){
        String sql = """
                INSERT INTO rag_eval_outbox
                (session_id, question, context, answer, found, eval_log_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(sql, sessionId, question, context, answer, found, evalLogId);
    }

    // 아직 발행되지 않은 행 id 목록 - relay 리스너의 시작/재연결 시 놓친 행을 보정하는 백필용 (FaqNotifyListener와 동일한 목적)
    public List<Long> findPendingIds(){
        String sql = """
                SELECT id FROM rag_eval_outbox
                WHERE status = 'PENDING'
                ORDER BY created_at
                LIMIT ?
                """;
        return jdbcTemplate.queryForList(sql, Long.class, RELAY_BACKFILL_BATCH_SIZE);
    }

    // FOR UPDATE SKIP LOCKED로 행을 클레임. NOTIFY는 브로드캐스트라 relay 인스턴스가 여러 개여도
    // 먼저 락을 잡은 인스턴스만 처리하고 나머지는 빈 결과를 받아 넘어간다
    public Optional<RagEvalOutboxRow> claimForPublish(Long id){
        String sql = """
                SELECT id, session_id, question, context, answer, found, eval_log_id
                FROM rag_eval_outbox
                WHERE id = ? AND status = 'PENDING'
                FOR UPDATE SKIP LOCKED
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RagEvalOutboxRow(
                rs.getLong("id"),
                rs.getLong("session_id"),
                rs.getString("question"),
                rs.getString("context"),
                rs.getString("answer"),
                rs.getBoolean("found"),
                (Long) rs.getObject("eval_log_id")
        ), id).stream().findFirst();
    }

    public void markPublished(Long id){
        jdbcTemplate.update(
                "UPDATE rag_eval_outbox SET status = 'PUBLISHED', published_at = now() WHERE id = ?",
                id);
    }

    // 재시도 횟수를 올리고, 한도를 넘으면 FAILED로 종결한다 (무한 재시도 방지)
    public void markFailed(Long id, String error, int maxAttempts){
        jdbcTemplate.update("""
                UPDATE rag_eval_outbox
                SET attempt_count = attempt_count + 1,
                    last_error = ?,
                    status = CASE WHEN attempt_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END
                WHERE id = ?
                """, error, maxAttempts, id);
    }
}

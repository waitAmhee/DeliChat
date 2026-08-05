package com.AIstudy.delichat.chat.dto;

// rag_eval_outbox 한 행의 스냅샷. outbox relay가 클레임한 뒤 Kafka 이벤트로 변환하는 데 쓴다.
public record RagEvalOutboxRow(
        Long id,
        Long sessionId,
        String question,
        String context,
        String answer,
        boolean found,
        Long evalLogId
) {
}

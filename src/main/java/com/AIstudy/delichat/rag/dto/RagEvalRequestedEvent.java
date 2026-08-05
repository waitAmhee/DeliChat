package com.AIstudy.delichat.rag.dto;

// RAG_EVAL_REQUESTED 토픽 payload. found 여부와 무관하게 채팅 답변 완료마다 발행된다.
public record RagEvalRequestedEvent(
        Long outboxId,
        Long sessionId,
        String question,
        String context,
        String answer,
        boolean found,
        // found=false면 null - rag_eval_log 행이 아예 없어서 judge가 채점할 대상이 없음
        Long evalLogId
) {
}

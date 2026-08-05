package com.AIstudy.delichat.rag.dto;

// RAG_EVAL_JUDGED 토픽 payload. judge 컨슈머가 실제로 채점을 완료한 건에 대해서만 발행된다
// (5~10% 샘플링을 통과해 LLM 호출까지 끝난 것만).
public record RagEvalJudgedEvent(
        // FAQ 개선 후보 추출 컨슈머가 faq_improvement_candidate의 FK로 쓴다
        Long outboxId,
        Long evalLogId,
        int faithfulness,
        int relevancy,
        int tone,
        String failureType,
        String reason
) {
}

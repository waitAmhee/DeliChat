package com.AIstudy.delichat.rag.dto;

public record MultiCriteriaJudgement(
        int faithfulness,
        int relevancy,
        int tone,
        String failureType,  // none | insufficient_context | hallucination
        String reason
) {
}

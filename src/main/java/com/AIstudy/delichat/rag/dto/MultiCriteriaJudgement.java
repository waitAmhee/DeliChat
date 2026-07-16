package com.AIstudy.delichat.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MultiCriteriaJudgement(
        int faithfulness,
        int relevancy,
        int tone,
        @JsonProperty("failure_type") String failureType,  // none | insufficient_context | hallucination
        String reason
) {
}

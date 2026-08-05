package com.AIstudy.delichat.rag.dto;

public record FaqContextResult(
        String context,
        boolean found,
        Long topFaqId,
        Double topSimilarity
) {
}

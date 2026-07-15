package com.AIstudy.delichat.rag.dto;

public record FaqSimilarResult(
        Long id,
        String category,
        String question,
        String answer,
        double similarity
) {
}

package com.AIstudy.delichat.rag.dto;

public record FaqSearchOutcome(
        String query,
        FaqContextResult result
) {
}

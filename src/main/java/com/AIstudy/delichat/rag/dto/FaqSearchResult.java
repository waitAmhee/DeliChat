package com.AIstudy.delichat.rag.dto;

public record FaqSearchResult(
        boolean found,
        String category,
        String answer
) {
    public static FaqSearchResult notFound(){
        return new FaqSearchResult(false, null, "죄송해요, 관련된 답변을 찾지 못했어요. 좀 더 자세히 말씀해주시겠어요?");
    }

    public static FaqSearchResult generated(String category, String answer) {
        return new FaqSearchResult(true, category, answer);
    }
}

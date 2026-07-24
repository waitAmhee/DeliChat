package com.AIstudy.delichat.rag.service;

import com.AIstudy.delichat.rag.dto.FaqContextResult;
import com.AIstudy.delichat.rag.dto.FaqSearchOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class FaqSearchTools {

    private static final String NOT_FOUND_MESSAGE = "관련 참고자료를 찾지 못했습니다.";
    public static final String FAQ_OUTCOME_HOLDER_KEY = "faqOutcomeHolder";
    private final FaqSearchService faqSearchService;

    @Tool(description = "배달 서비스 이용 방법, 정책, 환불/이물질 신고 절차 등 회사가 정한 규칙에 대한 " +
            "참고자료를 검색한다. 사용자 개인의 주문/배달 조회에는 사용하지 않는다.")
    public String searchFaq(@ToolParam(description = "검색할 독립적인 질문") String query, ToolContext toolContext) {
        // 1. 필요한 FAQ 검색 + 답변 생성에 넣은 컨텍스트 생성
        FaqContextResult result = faqSearchService.buildContextFor(query);

        // 2.ToolContext에서 검색 결과를 담아둘 holder
        Object holderValue = toolContext.getContext().get(FAQ_OUTCOME_HOLDER_KEY);

        // 3. 타입이 AtomicReference이면 검색 query와 결과를 나중에 꺼내볼 수 있게 함
        if (holderValue instanceof AtomicReference<?> holder) {
            @SuppressWarnings("unchecked")
            AtomicReference<FaqSearchOutcome> outcomeHolder = (AtomicReference<FaqSearchOutcome>) holder;
            outcomeHolder.set(new FaqSearchOutcome(query, result));
        }
        // 4. 검색 결과가 있으면 FAQ 컨텍스트를 LLM에게 반환, 없으면 찾지 못한다는 메시지 반환
        return result.found() ? result.context() : NOT_FOUND_MESSAGE;
    }
}

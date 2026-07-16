package com.AIstudy.delichat.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatAnswerService {

    private static final String MEMBER_ID_CONTEXT_KEY = "memberId";
    private static final String NO_CONTEXT_PLACEHOLDER = "(관련 참고자료 없음)";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            너는 배달 서비스의 고객센터 챗봇입니다.

            [도구 사용 규칙]
            사용자가 본인의 최근 주문, 배달 상태, 주문 취소 여부 등
            개인화된 동적 정보를 물으면 반드시 getMyRecentOrders 도구를 호출해서
            실제 데이터를 조회한 뒤 그 결과를 바탕으로 답하세요.
            도구 호출 없이 임의로 배달 상태나 주문 내역을 추측하거나 지어내지 마세요.

            [참고자료 사용 규칙]
            아래 참고자료가 있다면 그 범위 안에서만 답하고 지어내지 마세요.
            참고자료도 없고 도구로도 답할 수 없는 질문이면 모른다고 솔직히 말하세요.

            친절하고 간결한 존댓말로 답하세요.

            [참고자료]
            %s
            """;

    private final ChatClient toolCallingChatClient;

    public Flux<String> answerStream(String userQuestion, String faqContext, Long memberId) {
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(
                faqContext.isEmpty() ? NO_CONTEXT_PLACEHOLDER : faqContext);

        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(MEMBER_ID_CONTEXT_KEY, memberId);

        return toolCallingChatClient.prompt()
                .system(systemPrompt)
                .user(userQuestion)
                .toolContext(toolContext)
                .stream()
                .content();
    }
}
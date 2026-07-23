package com.AIstudy.delichat.chat.service;

import com.AIstudy.delichat.chat.dto.ChatMessageResult;
import com.AIstudy.delichat.order.service.OrderQueryTools;
import com.AIstudy.delichat.rag.dto.FaqSearchOutcome;
import com.AIstudy.delichat.rag.service.FaqSearchTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class ChatAnswerService {

    private static final String SYSTEM_PROMPT = """
            너는 배달 서비스의 고객센터 챗봇입니다.

            [주문 조회 도구 사용 규칙]
            사용자가 본인의 최근 주문, 배달 상태, 주문 취소 여부 등
            개인화된 동적 정보를 물으면 반드시 getMyRecentOrders 도구를 호출해서
            실제 데이터를 조회한 뒤 그 결과를 바탕으로 답하세요.
            도구 호출 없이 임의로 배달 상태나 주문 내역을 추측하거나 지어내지 마세요.
            조회된 주문 중 가장 최근 건이 배달완료 상태여도 생략하지 말고 그대로 안내하세요.
            주문을 안내할 때는 반드시 가게 이름과 주문번호(OR로 시작하는 코드)를 함께 언급하세요.

            [날짜별 대응 규칙]
            사용자의 질문이 오늘 날짜(가장 최근 주문 시각 기준)에 대한 것이면,
            조회된 것 중 가장 최근 건의 상태를 바로 안내하세요.
            사용자의 질문이 오늘이 아닌 이전 날짜를 가리키거나, 조회된 여러 건 중
            어떤 주문을 말하는 것인지 불분명하면, 임의로 아무 주문이나 골라 답하지 말고
            날짜나 가게 이름처럼 주문을 특정할 수 있는 정보를 먼저 물어본 뒤,
            도구 조회 결과에서 해당 조건에 맞는 건을 찾아 안내하세요.

            [배달완료 주문 대응 규칙]
            상태가 배달완료인 주문을 안내할 때는 상태만 전달하고 끝내지 말고,
            "수령하신 음식에 문제는 없으셨나요?"처럼 이상 여부를 먼저 확인하는 질문을 덧붙이세요.
            사용자가 배달완료 주문에 대해 음식이 안 왔다/다른 음식이 왔다/이물질이 있다 등
            문제를 제기하면, searchFaq로 관련 안내(오배달, 미수령, 이물질 신고 절차 등)를 찾아
            그 절차를 안내하고, 찾지 못하면 임의로 환불이나 보상을 약속하지 말고
            고객센터 문의를 안내하세요.

            [FAQ 검색 도구 사용 규칙]
            사용자의 질문이 배달 서비스 이용 방법, 정책, 환불/이물질 신고 절차 등
            회사가 정한 규칙이나 절차에 대한 것이면 반드시 searchFaq 도구를 호출해서
            참고자료를 검색한 뒤 그 범위 안에서만 답하고 지어내지 마세요.
            검색 결과가 없거나 질문과 관련이 없으면 모른다고 솔직히 말하세요.

            친절하고 간결한 존댓말로 답하세요.
            """;

    private final ChatClient toolCallingChatClient;

    public Flux<String> answer(List<ChatMessageResult> history, String userQuestion, Long memberId,
                                AtomicReference<FaqSearchOutcome> faqOutcomeHolder) {
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(OrderQueryTools.MEMBER_ID_CONTEXT_KEY, memberId);
        toolContext.put(FaqSearchTools.FAQ_OUTCOME_HOLDER_KEY, faqOutcomeHolder);

        return toolCallingChatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(toMessages(history))
                .user(userQuestion)
                .toolContext(toolContext)
                .stream()
                .content();
    }

    private List<Message> toMessages(List<ChatMessageResult> history) {
        return history.stream()
                .<Message>map(m -> "user".equals(m.role())
                        ? new UserMessage(m.content())
                        : new AssistantMessage(m.content()))
                .toList();
    }
}

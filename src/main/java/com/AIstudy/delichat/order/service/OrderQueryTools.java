package com.AIstudy.delichat.order.service;

import com.AIstudy.delichat.order.dto.OrderStatusResult;
import com.AIstudy.delichat.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderQueryTools {

    private static final int RECENT_ORDER_LIMIT = 5;
    public static final String MEMBER_ID_CONTEXT_KEY = "memberId";

    private final OrderRepository orderRepository;

    /**
     * memberId는 LLM이 채우는 파라미터가 아니라 ToolContext로만 전달받는다.
     * LLM이 임의의 memberId를 넣어 다른 회원의 주문을 조회하지 못하게 막기 위함.
     * 단, 이 설계는 세션 생성 시점에 클라이언트가 임의의 memberId를 넘기는 것까지는
     * 막지 못한다 (ChatController.createSession 참고) — 그건 별도의 인증 계층이 필요하다.
     */
    @Tool(description = "로그인한 사용자 본인의 최근 주문 목록과 배달 상태를 조회한다. " +
            "'내 주문', '배달 언제 와요', '주문 취소됐어요?' 처럼 사용자 본인에게 귀속된 " +
            "동적인 주문/배달 정보를 물을 때만 호출한다.")
    public String getMyRecentOrders(ToolContext toolContext) {
        // 1. 로그인 세션에서 memberId를 꺼냄
        Object memberIdValue = toolContext.getContext().get(MEMBER_ID_CONTEXT_KEY);
        if (!(memberIdValue instanceof Long memberId)) {
            return "로그인 정보가 없어 주문 조회가 불가능합니다. 로그인 후 다시 문의해 주세요.";
        }

        // 2. DB에서 실제 order 내역 조회
        List<OrderStatusResult> orders = orderRepository.findRecentOrdersByMember(memberId, RECENT_ORDER_LIMIT);

        if (orders.isEmpty()) {
            return "최근 주문 내역이 없습니다.";
        }

        // 3. 리턴된 문자열은 LLM에게 다시 전달돼서 LLM이 그걸 읽고 최종 답변을 만드는 데 씀
        // 주문마다 명확히 구분된 블록으로 반환 — 여러 건이 한 줄에 나열되면 소형 모델이 주문을 혼동하기 쉬움
        return orders.stream()
                .map(o -> """
                        [주문번호 %s] %s
                        - 메뉴: %s
                        - 금액: %d원
                        - 상태: %s
                        - 주문시각: %s""".formatted(
                        o.orderCode(), o.storeName(), o.menuSummary(),
                        o.totalPrice(), o.deliveryStatus(), o.orderedAt()))
                .collect(Collectors.joining("\n\n"));
    }
}
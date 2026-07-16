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
    private static final String MEMBER_ID_CONTEXT_KEY = "memberId";

    private final OrderRepository orderRepository;

    /**
     * memberId는 LLM이 채우는 파라미터가 아니라 ToolContext로만 전달받는다.
     * LLM이 임의의 memberId를 넣어 다른 회원의 주문을 조회하지 못하게 막기 위함.
     */
    @Tool(description = "로그인한 사용자 본인의 최근 주문 목록과 배달 상태를 조회한다. " +
            "'내 주문', '배달 언제 와요', '주문 취소됐어요?' 처럼 사용자 본인에게 귀속된 " +
            "동적인 주문/배달 정보를 물을 때만 호출한다.")
    public String getMyRecentOrders(ToolContext toolContext) {
        Object memberIdValue = toolContext.getContext().get(MEMBER_ID_CONTEXT_KEY);
        if (memberIdValue == null) {
            return "로그인 정보가 없어 주문 조회가 불가능합니다. 로그인 후 다시 문의해 주세요.";
        }

        Long memberId = (Long) memberIdValue;
        List<OrderStatusResult> orders = orderRepository.findRecentOrdersByMember(memberId, RECENT_ORDER_LIMIT);

        if (orders.isEmpty()) {
            return "최근 주문 내역이 없습니다.";
        }

        return orders.stream()
                .map(o -> "- [주문#%d] %s / %s / %d원 / 상태: %s / 주문시각: %s".formatted(
                        o.orderId(), o.storeName(), o.menuSummary(),
                        o.totalPrice(), o.deliveryStatus(), o.orderedAt()))
                .collect(Collectors.joining("\n"));
    }
}
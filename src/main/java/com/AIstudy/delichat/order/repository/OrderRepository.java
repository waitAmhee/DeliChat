package com.AIstudy.delichat.order.repository;

import com.AIstudy.delichat.order.dto.OrderStatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private static final DateTimeFormatter ORDERED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final JdbcTemplate jdbcTemplate;

    // 고객 ID에 따른 주문 내역 조회
    public List<OrderStatusResult> findRecentOrdersByMember(Long memberId, int limit){
        String sql = """
                SELECT id, order_code, store_name, menu_summary, total_price, delivery_status, ordered_at
                FROM orders
                WHERE member_id = ?
                ORDER BY ordered_at DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new OrderStatusResult(
                rs.getLong("id"),
                rs.getString("order_code"),
                rs.getString("store_name"),
                rs.getString("menu_summary"),
                rs.getInt("total_price"),
                rs.getString("delivery_status"),
                rs.getTimestamp("ordered_at").toLocalDateTime().format(ORDERED_AT_FORMAT)
        ), memberId, limit);
    }
}
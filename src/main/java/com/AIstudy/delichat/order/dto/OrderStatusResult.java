package com.AIstudy.delichat.order.dto;

public record OrderStatusResult(
        Long orderId,
        String orderCode,
        String storeName,
        String menuSummary,
        Integer totalPrice,
        String deliveryStatus,
        String orderedAt
) {
}
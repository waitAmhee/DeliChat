package com.AIstudy.delichat.order.dto;

public record OrderStatusResult(
        Long orderId,
        String storeName,
        String menuSummary,
        Integer totalPrice,
        String deliveryStatus,
        String orderedAt
) {
}
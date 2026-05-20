package com.team10.backend.domain.order.enums


enum class OrderStatus {
    PENDING,  // 주문 대기
    SUCCESS,  // 주문 성공
    CANCELED,
    EXPIRED// 주문 취소
}

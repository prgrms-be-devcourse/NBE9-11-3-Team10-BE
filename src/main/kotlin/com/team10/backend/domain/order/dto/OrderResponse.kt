package com.team10.backend.domain.order.dto

import com.team10.backend.domain.order.entity.Order

data class OrderResponse(
    val orderNumber: String,
    val totalAmount: Int,
    val userId: Long
) {
    companion object {
        fun from(order: Order): OrderResponse {
            return OrderResponse(
                orderNumber = order.orderNumber,
                totalAmount = order.totalAmount,
                userId = order.user.id
            )
        }
    }
}

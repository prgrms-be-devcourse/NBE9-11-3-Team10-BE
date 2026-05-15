package com.team10.backend.domain.order.dto

import com.team10.backend.domain.order.entity.Order

data class OrderResponse(
    @JvmField val orderNumber: String,
    @JvmField val totalAmount: Int,
    @JvmField val userId: Long
) {
    companion object {
        @JvmStatic
        fun from(order: Order): OrderResponse {
            return OrderResponse(
                orderNumber = order.orderNumber,
                totalAmount = order.totalAmount,
                userId = order.user.id
            )
        }
    }
}

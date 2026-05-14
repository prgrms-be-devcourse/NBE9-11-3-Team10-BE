package com.team10.backend.domain.order.dto.search

import com.team10.backend.domain.order.entity.OrderProducts

data class OrderItemDto(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val orderPrice: Int
) {
    companion object {
        fun from(op: OrderProducts): OrderItemDto {
            // Property 접근법을 사용하여 코드를 간결하게 유지
            return OrderItemDto(
                productId = op.product.id,
                productName = op.product.productName,
                quantity = op.quantity,
                orderPrice = op.orderPrice
            )
        }
    }
}
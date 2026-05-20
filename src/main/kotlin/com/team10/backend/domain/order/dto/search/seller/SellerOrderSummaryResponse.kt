package com.team10.backend.domain.order.dto.search.seller

import com.team10.backend.domain.order.entity.OrderProducts
import java.time.LocalDateTime

data class SellerOrderSummaryResponse(
    val orderNumber: String,
    val buyerName: String,
    val productName: String,
    val quantity: Int,
    val totalAmount: Int,
    val createdAt: LocalDateTime,
    val status: String
) {
    companion object {
        fun from(op: OrderProducts): SellerOrderSummaryResponse {

            val order = op.order ?: throw IllegalStateException("해당 상품에 연결된 주문이 없습니다.")
            val product = op.product

            val paymentStatus = order.payments.firstOrNull()?.status?.name ?: "READY"
            val user = order.user

            return SellerOrderSummaryResponse(
                orderNumber = order.orderNumber,
                buyerName = user.name,
                productName = product.productName,
                quantity = op.quantity,
                totalAmount = op.orderPrice * op.quantity,
                createdAt = order.createdAt,
                status = paymentStatus
            )
        }
    }
}

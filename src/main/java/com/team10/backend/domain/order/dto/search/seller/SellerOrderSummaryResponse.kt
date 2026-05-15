package com.team10.backend.domain.order.dto.search.seller

import com.team10.backend.domain.order.entity.OrderProducts
import com.team10.backend.domain.order.entity.Payment
import java.time.LocalDateTime
import java.util.function.Function

data class SellerOrderSummaryResponse(
    @JvmField  val orderNumber: String,
    @JvmField  val buyerName: String,
    @JvmField  val productName: String,
    @JvmField val quantity: Int,
    @JvmField val totalAmount: Int,
    @JvmField val createdAt: LocalDateTime,
    @JvmField val status: String
) {
    companion object {
        @JvmStatic
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
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
            // 1. 결제 상태 추출 (Stream 대신 코틀린 컬렉션 함수 사용)
            val paymentStatus = op.order.payments.firstOrNull()?.status?.name ?: "READY"

            // 2. 가독성을 위해 관련 객체를 미리 변수로 선언 (Property 접근법 활용)
            val order = op.order
            val product = op.product
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
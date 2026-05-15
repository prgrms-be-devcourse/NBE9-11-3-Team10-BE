package com.team10.backend.domain.order.dto.search.buyer

import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.OrderProducts
import com.team10.backend.domain.order.entity.Payment
import java.time.LocalDateTime
import java.util.function.Function

data class OrderSummaryResponse(
    @JvmField val orderNumber: String,
    @JvmField val totalAmount: Int,
    @JvmField val status: String,
    @JvmField val representativeProductName: String,
    @JvmField val totalQuantity: Int,
    @JvmField val createdAt: LocalDateTime
) {
    companion object {
        @JvmStatic
        fun from(order: Order): OrderSummaryResponse {
            // 1. 대표 상품명 가공 (코틀린의 문자열 템플릿 활용)
            val firstProduct = order.orderProducts.firstOrNull()?.product
            val productName = firstProduct?.productName ?: "알 수 없는 상품"
            val extraCount = order.orderProducts.size - 1

            val representativeName = if (extraCount > 0) {
                "$productName 외 ${extraCount}건"
            } else {
                productName
            }

            // 2. 총 수량 계산 (Stream 대신 코틀린 sumOf 활용)
            val totalQty = order.orderProducts.sumOf { it.quantity }

            // 3. 결제 상태 추출 (Safe call과 elvis 연산자 활용)
            val paymentStatus = order.payments.firstOrNull()?.status?.name ?: "READY"

            return OrderSummaryResponse(
                orderNumber = order.orderNumber,
                totalAmount = order.totalAmount,
                status = paymentStatus,
                representativeProductName = representativeName,
                totalQuantity = totalQty,
                createdAt = order.createdAt
            )
        }
    }
}
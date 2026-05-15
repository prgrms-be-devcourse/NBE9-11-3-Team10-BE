package com.team10.backend.domain.order.dto.search

import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.OrderProducts
import com.team10.backend.domain.order.entity.Payment
import java.time.LocalDateTime
import java.util.function.Function

/*
{
  "success": true,
  "data": {
    "orderNumber": "ORD-20240416-ABC123",
    "totalAmount": 150000,
    "paymentStatus": "READY",
    "createdAt": "2024-04-16T14:20:00",
    "delivery": {
      "deliveryAddress": "서울특별시 ...",
      "trackingNumber": "TRK-123456789"
    },
    "orderItems": [
      {
        "productId": 50,
        "productName": "애플 맥북 에어",
        "quantity": 1,
        "orderPrice": 110000
      },
      {
        "productId": 51,
        "productName": "맥북 전용 파우치",
        "quantity": 1,
        "orderPrice": 35000
      },
      {
        "productId": 52,
        "productName": "C타입 허브",
        "quantity": 1,
        "orderPrice": 5000
      }
    ]
  },
  "error": null
}
 */
data class OrderDetailResponse(
    @JvmField val orderNumber: String,
    @JvmField val totalAmount: Int,
    @JvmField val paymentStatus: String,
    @JvmField val createdAt: LocalDateTime,
    @JvmField val delivery: OrderDeliveryDto,
    @JvmField val orderItems: List<OrderItemDto> // MutableList와 ? 제거
) {
    companion object {
        @JvmStatic
        fun from(order: Order): OrderDetailResponse {
            // 1. 결제 상태 추출 (Safe call + Elvis 연산자)
            val paymentStatus = order.payments.firstOrNull()?.status?.name ?: "READY"

            // 2. 주문 아이템 리스트 변환 (Stream 대신 map 사용)
            // order.orderProducts가 null일 수 없다면 바로 map 실행
            val items = order.orderProducts.map { OrderItemDto.from(it) }

            return OrderDetailResponse(
                orderNumber = order.orderNumber,
                totalAmount = order.totalAmount,
                paymentStatus = paymentStatus,
                createdAt = order.createdAt,
                delivery = order.delivery?.let { OrderDeliveryDto.from(it) }
                    ?: throw IllegalArgumentException("배송 정보가 누락되었습니다."),
                orderItems = items
            )
        }
    }
}
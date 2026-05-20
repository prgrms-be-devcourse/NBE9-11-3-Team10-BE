package com.team10.backend.domain.order.dto.search

import com.team10.backend.domain.order.entity.OrderDelivery

// 배송 정보를 담는 내부 DTO,internal이 있었는데 생략
data class OrderDeliveryDto(
    val deliveryAddress: String,
    val trackingNumber: String? // 아직 발송 전일 수 있으므로 nullable 유지
) {
    companion object {
        fun from(delivery: OrderDelivery): OrderDeliveryDto {
            return OrderDeliveryDto(
                deliveryAddress = delivery.deliveryAddress,
                trackingNumber = delivery.trackingNumber
            )
        }
    }
}
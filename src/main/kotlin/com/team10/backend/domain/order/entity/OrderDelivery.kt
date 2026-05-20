package com.team10.backend.domain.order.entity

import com.team10.backend.domain.order.enums.DeliveryStatus
import com.team10.backend.global.entity.BaseEntity
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import jakarta.persistence.*

@Entity
@Table(name = "order_delivery")
class OrderDelivery(
    @Column(name = "delivery_address")
    var deliveryAddress: String, // 카멜케이스 적용, 주 생성자로 올림

    @Column(name = "tracking_number")
    var trackingNumber: String? = null // 송장은 초기값이 null일 수 있음
) : BaseEntity() {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status")
    var status: DeliveryStatus? = null
        protected set

    // == 비즈니스 로직 ==

    fun startReady() {
        this.status = DeliveryStatus.READY
    }

    fun updateTracking(trackingNumber: String) {
        this.trackingNumber = trackingNumber
        this.status = DeliveryStatus.SHIPPING
    }

    fun assignOrder(order: Order) {
        this.order = order
    }

    companion object {
        fun builder() = OrderDeliveryBuilder()
    }

    // == 기존 서비스 레이어 호환을 위한 빌더 ==
    class OrderDeliveryBuilder {
        private var deliveryAddress: String? = null
        private var trackingNumber: String? = null

        // 자바 서비스에서 delivery_address()로 호출하고 있을 경우를 대비해 메서드명 유지
        fun delivery_address(deliveryAddress: String) = apply {
            this.deliveryAddress = deliveryAddress
        }

        fun tracking_number(trackingNumber: String?) = apply {
            this.trackingNumber = trackingNumber
        }

        fun build(): OrderDelivery {
            return OrderDelivery(
                deliveryAddress = deliveryAddress ?: throw BusinessException(ErrorCode.SHIPPING_ADDRESS_REQUIRED),
                trackingNumber = trackingNumber
            )
        }

        override fun toString(): String {
            return "OrderDelivery.OrderDeliveryBuilder(deliveryAddress=$deliveryAddress, trackingNumber=$trackingNumber)"
        }
    }
}

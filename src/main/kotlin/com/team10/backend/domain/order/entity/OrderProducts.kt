package com.team10.backend.domain.order.entity

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.global.entity.BaseEntity
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import jakarta.persistence.*

@Entity
@Table(name = "order_products")
class OrderProducts(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product, // 불변 프로퍼티

    @Column(nullable = false)
    var quantity: Int,

    @Column(nullable = false)
    var orderPrice: Int // 주문 당시의 가격
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null
        // protected set을 사용하여 외부 직접 할당은 막고 JPA 프록시/연관관계 메서드에는 열어둠
        protected set

    // == 연관관계 편의 메서드 ==
    fun assignOrder(order: Order) {
        this.order = order
    }

    companion object {
        fun builder() = OrderProductsBuilder()
    }

    // 기존 자바 서비스 코드와의 호환성을 위한 빌더
    class OrderProductsBuilder {
        private var product: Product? = null
        private var quantity: Int = 0
        private var orderPrice: Int = 0

        fun product(product: Product?) = apply { this.product = product }
        fun quantity(quantity: Int) = apply { this.quantity = quantity }
        fun orderPrice(orderPrice: Int) = apply { this.orderPrice = orderPrice }

        fun build(): OrderProducts {
            return OrderProducts(
                product = product ?: throw BusinessException(ErrorCode.PRODUCT_NOT_FOUND),
                quantity = quantity,
                orderPrice = orderPrice
            )
        }
    }
}

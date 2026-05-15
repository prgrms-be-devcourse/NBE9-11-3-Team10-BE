package com.team10.backend.domain.order.entity

import com.team10.backend.domain.order.enums.OrderStatus
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.entity.BaseEntity
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.util.function.Consumer

@Entity
@Table(name = "orders")
@SQLDelete(sql = "UPDATE orders SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
class Order(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User, // 불변성 유지

    @Column(name = "order_number", nullable = false, unique = true)
    val orderNumber: String,

    @Column(name = "total_amount")
    var totalAmount: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "is_deleted")
    var isDeleted: Boolean = false
) : BaseEntity() {

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val orderProducts: MutableList<OrderProducts> = mutableListOf()

    @OneToOne(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var delivery: OrderDelivery? = null
        protected set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _payments: MutableList<Payment> = mutableListOf()
    val payments: List<Payment> get() = _payments

    // == 연관관계 및 비즈니스 로직 ==

    fun assignDelivery(delivery: OrderDelivery) {
        this.delivery = delivery
        delivery.assignOrder(this)
    }

    fun addOrderProduct(orderProduct: OrderProducts) {
        this.orderProducts.add(orderProduct)
        orderProduct.assignOrder(this)
    }

    fun addPayment(payment: Payment) {
        this._payments.add(payment)
        payment.assignOrder(this)
    }

    fun cancelStatusOrder() {
        this.status = OrderStatus.CANCELED
    }

    fun successStatusOrder() {
        this.status = OrderStatus.SUCCESS
    }

    companion object {
        // 기존 서비스 코드와의 호환성을 위한 빌더 (코틀린에서는 생성자를 선호하지만 유지함)
        fun builder() = OrderBuilder()

        // 생성 메서드 (서비스 레이어에서 사용)
        @JvmStatic
        fun createOrder(
            user: User,
            orderNumber: String,
            delivery: OrderDelivery,
            items: List<OrderProducts>
        ): Order {
            val calculatedTotalAmount = items.sumOf { it.orderPrice * it.quantity }

            val order = Order(
                user = user,
                orderNumber = orderNumber,
                totalAmount = calculatedTotalAmount
            )

            // 양방향 관계 설정
            order.assignDelivery(delivery)
            items.forEach { order.addOrderProduct(it) }

            // 초기 결제 객체 생성 및 포함
            val initialPayment = Payment.builder()
                .order(order)
                .orderNumber(orderNumber)
                .totalAmount(calculatedTotalAmount)
                .status(PaymentStatus.READY)
                .idempotencyKey(null)
                .build()

            order.addPayment(initialPayment)

            return order
        }
    }

    // 기존 자바 빌더와의 호환성을 위한 내부 클래스
    class OrderBuilder {
        private var user: User? = null
        private var orderNumber: String? = null
        private var totalAmount: Int = 0

        fun user(user: User?) = apply { this.user = user }
        fun orderNumber(orderNumber: String?) = apply { this.orderNumber = orderNumber }
        fun totalAmount(totalAmount: Int) = apply { this.totalAmount = totalAmount }

        fun build(): Order {
            return Order(
                user = user ?: throw BusinessException(ErrorCode.USER_NOT_FOUND),
                orderNumber = orderNumber ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND),
                totalAmount = totalAmount
            )
        }
    }
}
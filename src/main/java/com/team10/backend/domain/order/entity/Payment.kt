package com.team10.backend.domain.order.entity

import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.enums.RequestType
import com.team10.backend.global.entity.BaseEntity
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import jakarta.persistence.*

@Entity
@Table(
    name = "payments"
)
class Payment(
    @Column(name = "order_number", nullable = false)
    val orderNumber: String, // 결제 번호는 생성 시 필수이자 불변

    @Column(name = "total_amount", nullable = false)
    val totalAmount: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus = PaymentStatus.READY,

    @Column(name = "idempotency_key")
    var idempotencyKey: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    val type: RequestType = RequestType.PAYMENT
) : BaseEntity() {

    @Column(name = "payment_key")
    var paymentKey: String? = null
        protected set

    @Column(columnDefinition = "TEXT")
    var responseBody: String? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null
        protected set

    // == 비즈니스 메서드 (기존 로직 유지) ==

    fun completePayment(paymentKey: String) {
        this.paymentKey = paymentKey
        this.status = PaymentStatus.PAID
    }

    fun complete(responseBody: String) {
        this.status = PaymentStatus.PAID
        this.responseBody = responseBody
    }

    fun failPayment() {
        this.status = PaymentStatus.FAILED
    }

    fun expirePayment() {
        this.status = PaymentStatus.EXPIRED
    }


    fun markAsPending() {
        this.status = PaymentStatus.PENDING
    }

    fun markAsUncertain() {
        this.status = PaymentStatus.UNCERTAIN
    }

    fun assignOrder(order: Order) {
        this.order = order
    }

    // 기존 자바의 getLastTossKey() 호환
    fun getLastTossKey(): String? = this.idempotencyKey

    companion object {
        @JvmStatic
        fun createPayment(
            order: Order,
            orderNumber: String,
            amount: Int,
            idempotencyKey: String?,
            type: RequestType?
        ): Payment {
            return builder()
                .order(order)
                .orderNumber(orderNumber)
                .totalAmount(amount)
                .idempotencyKey(idempotencyKey)
                .status(PaymentStatus.READY)
                .type(type ?: RequestType.PAYMENT)
                .build()
        }

        @JvmStatic
        fun builder() = PaymentBuilder()
    }

    // == 자바 서비스 레이어 호환용 빌더 ==
    class PaymentBuilder {
        private var order: Order? = null
        private var orderNumber: String? = null
        private var totalAmount: Int = 0
        private var status: PaymentStatus = PaymentStatus.READY
        private var idempotencyKey: String? = null
        private var type: RequestType = RequestType.PAYMENT

        fun order(order: Order?) = apply { this.order = order }
        fun orderNumber(orderNumber: String?) = apply { this.orderNumber = orderNumber }
        fun totalAmount(totalAmount: Int) = apply { this.totalAmount = totalAmount }
        fun status(status: PaymentStatus?) = apply { if (status != null) this.status = status }
        fun idempotencyKey(idempotencyKey: String?) = apply { this.idempotencyKey = idempotencyKey }
        fun type(type: RequestType?) = apply { if (type != null) this.type = type }

        fun build(): Payment {
            val payment = Payment(
                orderNumber = orderNumber ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND),
                totalAmount = totalAmount,
                status = status,
                idempotencyKey = idempotencyKey,
                type = type
            )
            order?.let { payment.assignOrder(it) }
            return payment
        }
    }
}
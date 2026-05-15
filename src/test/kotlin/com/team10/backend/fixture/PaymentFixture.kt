package com.team10.backend.fixture

import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.enums.RequestType
import net.datafaker.Faker
import java.util.*

object PaymentFixture {
    private val faker = Faker()

    // ─────────────────────────────────────────────
    // ✅ Payment 생성 팩토리 (상태별 시나리오)
    // ─────────────────────────────────────────────

    /**
     * [기본] 결제 준비 상태 (READY)
     * - Order.createOrder() 호출 시 자동 생성되는 초기 상태
     */
    fun createReady(
        order: Order = OrderFixture.create(),
        orderNumber: String = order.orderNumber,
        amount: Int = order.totalAmount,
        idempotencyKey: String = UUID.randomUUID().toString(),
        type: RequestType = RequestType.PAYMENT
    ): Payment {
        return Payment.createPayment(order, orderNumber, amount, idempotencyKey, type)
        // createPayment 내부에서 status = READY 로 설정됨
    }

    /**
     * [시나리오] 결제 진행 중 (PENDING)
     * - 토스 결제 승인 요청 후 응답 대기 상태
     */
    fun createPending(
        order: Order = OrderFixture.create(),
        orderNumber: String = order.orderNumber,
        amount: Int = order.totalAmount,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Payment {
        return createReady(order, orderNumber, amount, idempotencyKey).apply {
            markAsPending()
        }
    }

    /**
     * [시나리오] 결제 완료 (PAID)
     * - paymentKey 와 응답 본문 포함
     */
    fun createPaid(
        order: Order = OrderFixture.create(),
        orderNumber: String = order.orderNumber,
        amount: Int = order.totalAmount,
        paymentKey: String = "test_pk_${UUID.randomUUID()}",
        idempotencyKey: String = UUID.randomUUID().toString(),
        responseBody: String = """{"status":"DONE","amount":$amount}"""
    ): Payment {
        return createReady(order, orderNumber, amount, idempotencyKey).apply {
            completePayment(paymentKey)
            complete(responseBody)
        }
    }

    /**
     * [시나리오] 결제 실패 (FAILED)
     */
    fun createFailed(
        order: Order = OrderFixture.create(),
        orderNumber: String = order.orderNumber,
        amount: Int = order.totalAmount,
        idempotencyKey: String = UUID.randomUUID().toString(),
        failureReason: String = "CARD_REJECTED"
    ): Payment {
        return createReady(order, orderNumber, amount, idempotencyKey).apply {
            failPayment()
            // responseBody = """{"errorCode":"$failureReason"}"""
        }
    }

    /**
     * [시나리오] 결제 상태 불확정 (UNCERTAIN)
     * - 네트워크 타임아웃 등으로 토스 응답을 받지 못한 경우
     */
    fun createUncertain(
        order: Order = OrderFixture.create(),
        orderNumber: String = order.orderNumber,
        amount: Int = order.totalAmount,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Payment {
        return createReady(order, orderNumber, amount, idempotencyKey).apply {
            markAsUncertain()
        }
    }

    /**
     * [시나리오] 환불/취소 요청 (RequestType = CANCEL)
     */
    fun createForCancel(
        originalPayment: Payment, // 원래 결제 내역 참조
        cancelAmount: Int = originalPayment.totalAmount,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Payment {
        return Payment.builder()
            .order(originalPayment.order)
            .orderNumber(originalPayment.orderNumber)
            .totalAmount(cancelAmount)
            .status(PaymentStatus.READY)
            .idempotencyKey(idempotencyKey)
            .type(RequestType.CANCEL)
            .build()
    }

    // ─────────────────────────────────────────────
    // ⚠️ 상태 전이 검증용 헬퍼 (PaymentStatus.canTransitionTo 테스트)
    // ─────────────────────────────────────────────

    /**
     * 주어진 상태에서 다음 상태로의 전이가 가능한지 테스트용 팩토리
     */
    data class TransitionTest(
        val from: PaymentStatus,
        val to: PaymentStatus,
        val expected: Boolean
    )

    fun generateTransitionTestCases(): List<TransitionTest> {
        return listOf(
            // READY 에서 가능한 전이
            TransitionTest(PaymentStatus.READY, PaymentStatus.PENDING, true),
            TransitionTest(PaymentStatus.READY, PaymentStatus.FAILED, true),
            TransitionTest(PaymentStatus.READY, PaymentStatus.PAID, false), // 직접 PAID 불가

            // PENDING 에서 가능한 전이
            TransitionTest(PaymentStatus.PENDING, PaymentStatus.PAID, true),
            TransitionTest(PaymentStatus.PENDING, PaymentStatus.FAILED, true),
            TransitionTest(PaymentStatus.PENDING, PaymentStatus.UNCERTAIN, true),

            // UNCERTAIN 에서 가능한 전이
            TransitionTest(PaymentStatus.UNCERTAIN, PaymentStatus.PAID, true),
            TransitionTest(PaymentStatus.UNCERTAIN, PaymentStatus.FAILED, true),

            // 최종 상태에서는 전이 불가
            TransitionTest(PaymentStatus.PAID, PaymentStatus.FAILED, false),
            TransitionTest(PaymentStatus.FAILED, PaymentStatus.PENDING, false)
        )
    }

    // ─────────────────────────────────────────────
    // 🔁 유틸리티
    // ─────────────────────────────────────────────

    /**
     * 유니크한 idempotencyKey 생성
     * - 멱등성 테스트 시 동일 키 재사용 또는 신규 키 생성 용도
     */
    fun generateIdempotencyKey(prefix: String = "idem"): String {
        return "$prefix-${UUID.randomUUID()}"
    }
}
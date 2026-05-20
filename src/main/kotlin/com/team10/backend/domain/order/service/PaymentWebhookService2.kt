package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import com.team10.backend.domain.order.dto.webhook.WebhookPayload
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.stereotype.Service

@Service
class PaymentWebhookService2(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentUpdateService: PaymentUpdateService,
    private val paymentStatusService: PaymentStatusService
) {

    fun processWebhook(payload: WebhookPayload) {
        val orderId = payload.data.orderId
        val tossServerStatus = payload.data.status // DONE, ABORTED, EXPIRED, CANCELED 등
        val paymentKey = payload.data.paymentKey

        // 1. 1차 필터링
        val order = orderRepository.findByOrderNumber(orderId)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        val payment = paymentRepository.findFirstByOrderOrderByCreatedAtDesc(order)
            ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

        if (payment.paymentKey != null && payment.paymentKey != paymentKey) {
            // 다른 결제 시도 건에 대한 웹훅이므로 꼬이지 않게 무시하거나 예외 처리
            return
        }

        val currentStatus = payment.status
        val nextTossStatus = convertToPaymentStatus(tossServerStatus)

        // 3. 상태 전이 유효성 검사 (멱등성 및 순서 뒤바뀜 1차 차단)
        if (!currentStatus.canTransitionTo(nextTossStatus)) {
             return // 토스에게 200 OK를 주기 위해 예외를 던지지 않고 무시(return)
        }

        // 특수 상태 예외 처리: 만료 처리 시 주문 재고를 다시 환원
        if (tossServerStatus == "EXPIRED") {
            paymentUpdateService.rollbackStockAndCancelOrder(orderId, tossServerStatus, nextTossStatus, paymentKey)
//            log.info("유효기간이 만료된 결제건 웹훅입니다. orderId=$orderId")
            return
        }

        // 2. 토스 서버 상태별 매핑 및 분기
        when (tossServerStatus) {
            "DONE" -> {
                val response = TossConfirmResponse.from(payload)
                paymentUpdateService.completeOrderAndFinalizeRecord(orderId, paymentKey, response)
            }

            "ABORTED" -> {
                //  구매자 단순 실패이므로 재고는 그대로 두고 결제만 FAILED 처리
                paymentStatusService.finalizeRecord(payment, PaymentStatus.FAILED, null)
            }

            "CANCELED" -> {
                // todo재고 감소 처리해야 함
                paymentStatusService.finalizeRecord(payment, PaymentStatus.CANCELED, null)
            }
        }
    }

    // 토스 상태 -> 내 결제 상태 매핑 헬퍼 메서드
    private fun convertToPaymentStatus(tossStatus: String): PaymentStatus = when (tossStatus) {
        "DONE" -> PaymentStatus.PAID
        "ABORTED" -> PaymentStatus.FAILED
        "CANCELED" -> PaymentStatus.CANCELED
        "EXPIRED" -> PaymentStatus.EXPIRED
        else -> PaymentStatus.UNCERTAIN // 알 수 없는 상태거나 예외적인 경우 안전하게 UNCERTAIN으로 매핑
    }
}
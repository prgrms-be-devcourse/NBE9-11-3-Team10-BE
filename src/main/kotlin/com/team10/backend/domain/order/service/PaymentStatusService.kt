package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import com.team10.backend.domain.order.dto.webhook.WebhookPayload
import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.enums.RequestType
import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.*

@Service
class PaymentStatusService(
    private val paymentRepository: PaymentRepository,
    private val objectMapper: ObjectMapper
) {

    // 네트워크 에러 발생 시 호출:
    @Transactional
    fun markRecordAsUncertain(record: Payment) {
        // 엔티티의 상속받은 id 프로퍼티 사용 및 Optional SAM 변환 간소화
        val existing = paymentRepository.findById(record.id)
            .orElseThrow { BusinessException(ErrorCode.PAYMENT_NOT_FOUND) }

        existing.markAsUncertain() // 엔티티 상태 변경
        paymentRepository.saveAndFlush(existing)
    }

    @Transactional
    fun getOrCreatePaymentAttempt(order: Order, type: RequestType,idempotencyKey: String): Payment {
        // 1. 최신 레코드 조회 (UNCERTAIN 등의 경우에 이전 결제 상태 값이 필요함)
        val latestPayment = paymentRepository.findFirstByOrderOrderByCreatedAtDesc(order)

        if (latestPayment != null) {
            // 기존 결제 시도가 있는 경우 분기 처리 (PAID, PENDING, UNCERTAIN 등)
            val result = handleExistingPayment(latestPayment)
            if (result != null) {
                return result
            }
        }
        // 2. 신규 생성 (FAILED 이후 혹은 최초 생성)
        return createNewPayment(order, type,idempotencyKey)
    }

    private fun createNewPayment(order: Order, type: RequestType,idempotencyKey: String): Payment {
        val tossOrderNumber = order.orderNumber

        // Payment 컴패니언 객체의 생성 메서드 직접 호출
        val newPayment = Payment.createPayment(
            order = order,
            orderNumber = tossOrderNumber,
            amount = order.totalAmount,
            idempotencyKey = idempotencyKey,
            type = type // payment 승인 혹은 cancel 환불
        )

        return paymentRepository.saveAndFlush(newPayment)
    }

    // 상태 분기 로직 공통화
    private fun handleExistingPayment(curPayment: Payment): Payment? {
        return when (curPayment.status) {
            PaymentStatus.PAID -> curPayment
            PaymentStatus.READY -> {
                // READY 상태인 경우 PENDING으로 변경 후 반환
                curPayment.markAsPending()
                curPayment
            }
            PaymentStatus.UNCERTAIN -> {
                curPayment.markAsPending() // READY와 동일하게 PENDING으로만 전환
                curPayment
            }

            PaymentStatus.PENDING -> throw BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT)

            PaymentStatus.FAILED -> null // null 반환 시 상위 메서드에서 createNewPayment() 호출

            else -> throw BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT) // 정의되지 않은 상태 가드
        }
    }


    @Transactional
    fun finalizeRecord(record: Payment, status: PaymentStatus, response: TossConfirmResponse?) {
        // 1. 최신 상태 조회 및 비관적 락 획득
        val payment = paymentRepository.findByIdForUpdate(record.id)
            ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

        // 2. 레이스 컨디션 방어/이미 PAID인 경우 (웹훅이 먼저 처리한 경우 등) 바로 리턴
        if (payment.status == PaymentStatus.PAID) {
            return
        }

        // 3. 비즈니스 상태 전이 및 결과 직렬화
        if (status == PaymentStatus.PAID) {
            val jsonResponse = serializeResponse(response)
            payment.complete(jsonResponse)
        } else {
            payment.failPayment()
        }

        // 최신 락 객체를 명시적으로 플러시
        paymentRepository.saveAndFlush(payment)
    }

    @Transactional
    fun finalizeRecordFromWebhook(record: Payment, payload: WebhookPayload) {
        val response = TossConfirmResponse.from(payload)

        // 결제 완료(PAID) 상태로 최종 기록 확정
        finalizeRecord(
            record = record,
            status = PaymentStatus.PAID,
            response = response
        )
    }

    // JSON -> 객체 변환 (재사용 시)
    fun parseResponse(responseBody: String): TossConfirmResponse {
        if (responseBody.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_REQUEST) // 혹은 적절한 예외 처리
        }

        // Jackson 코틀린 확장 함수를 사용하면 구체적인 타입을 생략하거나
        // readValue(responseBody) 형태로만 호출
        return try {
            objectMapper.readValue(responseBody, TossConfirmResponse::class.java)
        } catch (e: Exception) {
            throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR)
        }
    }

    // 객체 -> JSON 변환 (저장 시)
    fun serializeResponse(response: TossConfirmResponse?): String {
        // response가 null일 경우 Jackson은 "null"이라는 문자열을 반환하므로,
        // DB 저장용 빈 문자열("")이나 예외 처리를 엘비스 연산자로 명시하는 것이 안전.
        return response?.let { objectMapper.writeValueAsString(it) } ?: ""
    }
}

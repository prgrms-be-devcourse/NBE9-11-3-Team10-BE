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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.ObjectMapper

@ExtendWith(MockitoExtension::class)
class PaymentStatusServiceKTest {

    @InjectMocks
    private lateinit var paymentStatusService: PaymentStatusService

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var objectMapper: ObjectMapper

    // 테스트용 가짜 공통 객체 생성 유틸
    private fun createMockOrder(): Order {
        val mockOrder = mock(Order::class.java)
        whenever(mockOrder.orderNumber).thenReturn("ORD-2026-TEST")
        whenever(mockOrder.totalAmount).thenReturn(15000)
        return mockOrder
    }

    //  헬퍼 메서드
    private fun createPendingPayment(): Payment {
        val mockOrder = mock(Order::class.java)
        val payment = Payment.builder()
            .order(mockOrder)
            .orderNumber("ORD-2026-FINALIZE")
            .totalAmount(25000)
            .status(PaymentStatus.PENDING) // 기본 검증 상태인 PENDING 설정
            .build()
        ReflectionTestUtils.setField(payment, "id", 99L)
        return payment
    }

    // =========================================================================
    // 성공 케이스
    // =========================================================================

    @Test
    @DisplayName("시나리오 S1-1: 최초 결제 시도 (이전 기록 없음) - 신규 결제 레코드가 생성되어 반환된다")
    fun success_S1_1_first_attempt_creates_new_payment() {
        // given
        val order = createMockOrder()
        val savedPayment = Payment.builder().order(order).orderNumber(order.orderNumber).totalAmount(order.totalAmount).status(PaymentStatus.READY).build()

        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(order)).thenReturn(null)
        whenever(paymentRepository.saveAndFlush(any<Payment>())).thenReturn(savedPayment)

        // when
        val result = paymentStatusService.getOrCreatePaymentAttempt(order, RequestType.PAYMENT,"test_idempotency_key")

        // then
        assertNotNull(result)
        assertEquals(PaymentStatus.READY, result.status)
        verify(paymentRepository, times(1)).saveAndFlush(any<Payment>())
    }

    @Test
    @DisplayName("시나리오 S1-2: 이전 결제 시도가 완전히 실패(FAILED)한 상태 - 기존 기록은 무시하고 신규 결제 레코드를 생성한다")
    fun success_S1_2_previous_failed_creates_new_payment() {
        // given
        val order = createMockOrder()
        val previousFailedPayment = Payment.builder().order(order).orderNumber(order.orderNumber).totalAmount(order.totalAmount).status(PaymentStatus.FAILED).build()
        val newPayment = Payment.builder().order(order).orderNumber(order.orderNumber).totalAmount(order.totalAmount).status(PaymentStatus.READY).build()

        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(order)).thenReturn(previousFailedPayment)
        whenever(paymentRepository.saveAndFlush(any<Payment>())).thenReturn(newPayment)

        // when
        val result = paymentStatusService.getOrCreatePaymentAttempt(order, RequestType.PAYMENT,"test_idempotency_key")

        // then
        assertNotNull(result)
        assertEquals(PaymentStatus.READY, result.status)
        verify(paymentRepository, times(1)).saveAndFlush(any<Payment>())
    }

    @Test
    @DisplayName("시나리오 S1-3: 이전 결제 시도가 이미 완료(PAID)된 상태 - 추가 생성 없이 기존 PAID 레코드를 그대로 반환한다")
    fun success_S1_3_previous_paid_returns_directly() {
        // given
        val order = createMockOrder()
        val previousPaidPayment = Payment.builder().order(order).orderNumber(order.orderNumber).totalAmount(order.totalAmount).status(PaymentStatus.PAID).build()

        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(order)).thenReturn(previousPaidPayment)

        // when
        val result = paymentStatusService.getOrCreatePaymentAttempt(order, RequestType.PAYMENT,"test_idempotency_key")

        // then
        assertNotNull(result)
        assertEquals(PaymentStatus.PAID, result.status)
        verify(paymentRepository, times(0)).saveAndFlush(any<Payment>()) // 신규 생성이 호출되지 않아야 함
    }

    @Test
    @DisplayName("시나리오 S1-4: 네트워크 에러 상태(UNCERTAIN)에서 성공적으로 재선점 - 기존 Idempotency-Key를 유지하며 PENDING으로 전환 후 반환한다")
    fun success_S1_4_uncertain_resumed_to_pending() {
        // given
        val order = createMockOrder()
        val uncertainPayment = Payment.builder().order(order).orderNumber(order.orderNumber).totalAmount(order.totalAmount).status(PaymentStatus.UNCERTAIN).idempotencyKey("idempotency-key-123").build()

        ReflectionTestUtils.setField(uncertainPayment, "id", 1L)

        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(order)).thenReturn(uncertainPayment)
//        whenever(paymentRepository.updateStatusFromUncertainToPending(uncertainPayment.id)).thenReturn(1) // 원자적 쿼리 선점 성공 승인(1 row)

        // when
        val result = paymentStatusService.getOrCreatePaymentAttempt(order, RequestType.PAYMENT,"test_idempotency_key")

        // then
        assertNotNull(result)
        assertEquals(PaymentStatus.PENDING, result.status) // 상태가 PENDING으로 전이됨을 검증
        assertEquals("idempotency-key-123", result.idempotencyKey) // 기존 키가 소실되지 않고 유지됨을 검증
        verify(paymentRepository, times(0)).saveAndFlush(any<Payment>()) // 신규 생성이 아니므로 생성 API는 안타야함
    }

    // =========================================================================
    //  예외/실패 케이스
    // =========================================================================

    @Test
    @DisplayName("시나리오 F1-1: 이미 다른 스레드가 결제를 진행 중인 경우(PENDING) - ALREADY_PROCESSED_PAYMENT 예외가 발생한다")
    fun fail_F1_1_already_pending_throws_exception() {
        // given
        val order = createMockOrder()
        val pendingPayment = Payment.builder().order(order).orderNumber(order.orderNumber).totalAmount(order.totalAmount).status(PaymentStatus.PENDING).build()

        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(order)).thenReturn(pendingPayment)

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentStatusService.getOrCreatePaymentAttempt(order, RequestType.PAYMENT,"test_idempotency_key")
        }

        assertEquals(ErrorCode.ALREADY_PROCESSED_PAYMENT, exception.errorCode)
    }



    // =========================================================================
    //  성공 케이스- finalizerecord 메서드 테스트
    // =========================================================================

    @Test
    @DisplayName("시나리오 S2-1: 결제 승인 완료 최종 확정 - PENDING 상태의 결제가 정상적으로 PAID로 전환되고 응답 본문이 직렬화되어 저장된다")
    fun success_S2_1_finalize_as_paid() {
        // given
        val payment = createPendingPayment()
        val mockResponse =
            TossConfirmResponse(paymentKey = "pk_success_123", orderId = "ORD-2026-FINALIZE", status = "DONE")
        val expectedJson = """{"status":"DONE"}"""

        whenever(paymentRepository.findByIdForUpdate(payment.id)).thenReturn(payment)
        whenever(objectMapper.writeValueAsString(mockResponse)).thenReturn(expectedJson)
        whenever(paymentRepository.saveAndFlush(payment)).thenReturn(payment)

        // when
        paymentStatusService.finalizeRecord(payment, PaymentStatus.PAID, mockResponse)

        // then
        assertEquals(PaymentStatus.PAID, payment.status) // 상태 검증
        assertEquals(expectedJson, payment.responseBody) // 직렬화 데이터 검증
        verify(paymentRepository, times(1)).saveAndFlush(payment)
    }

    @Test
    @DisplayName("시나리오 S2-2: 결제 실패 최종 확정 - PENDING 상태의 결제가 FAILED 상태로 전이된다")
    fun success_S2_2_finalize_as_failed() {
        // given
        val payment = createPendingPayment()

        whenever(paymentRepository.findByIdForUpdate(payment.id)).thenReturn(payment)
        whenever(paymentRepository.saveAndFlush(payment)).thenReturn(payment)

        // when
        paymentStatusService.finalizeRecord(payment, PaymentStatus.FAILED, null)

        // then
        assertEquals(PaymentStatus.FAILED, payment.status) // 상태 전이 검증
        verify(paymentRepository, times(1)).saveAndFlush(payment)
        verify(objectMapper, never()).writeValueAsString(any()) // 실패 시 직렬화가 타지 않아야 함
    }

    @Test
    @DisplayName("시나리오 S2-3: 레이스 컨디션 방어 - 비관적 락을 획득하고 보니 이미 웹훅 등으로 PAID 상태라면 Early Return 처리된다")
    fun success_S2_3_race_condition_already_paid_returns_immediately() {
        // given
        val payment = createPendingPayment()
        payment.completePayment("pk_already_done") // 락을 잡기 전 이미 누군가 PAID로 바꾼 상황 연출

        whenever(paymentRepository.findByIdForUpdate(payment.id)).thenReturn(payment)

        // when
        paymentStatusService.finalizeRecord(payment, PaymentStatus.PAID, mock(TossConfirmResponse::class.java))

        // then
        assertEquals(PaymentStatus.PAID, payment.status) // 여전히 PAID 유지
        verify(paymentRepository, never()).saveAndFlush(any()) //더 이상 저장 쿼리가 나가지 않아야 함
    }

    @Test
    @DisplayName("시나리오 S2-4(웹훅 확장): 토스 웹훅 Payload 인입 시, 정산 대상을 식별하여 최종 PAID 상태로 기록을 확정한다")
    fun success_S2_4_finalize_from_webhook_flow() {
        // given
        val payment = createPendingPayment()

        // 1. WebhookPayload 내부의 중첩 데이터 구조를 함께 모킹
        val mockPayload = mock(WebhookPayload::class.java)
        val mockData = mock(WebhookPayload.Data::class.java) // 내부 Data 객체 모킹

        // 2. 실제 프로덕션 코드(TossConfirmResponse.from)가 동작할 때 필요한 필드값을 세팅
        whenever(mockPayload.data).thenReturn(mockData)
        whenever(mockData.paymentKey).thenReturn("pk_webhook_789")
        whenever(mockData.orderId).thenReturn("ORD-2026-FINALIZE")
        whenever(mockData.status).thenReturn("DONE")

        val mockResponse = TossConfirmResponse(paymentKey = "pk_webhook_789", orderId = "ORD-2026-FINALIZE", status = "DONE")
        val expectedJson = """{"status":"DONE"}"""

        whenever(paymentRepository.findByIdForUpdate(payment.id)).thenReturn(payment)
        whenever(objectMapper.writeValueAsString(any<TossConfirmResponse>())).thenReturn(expectedJson)
        whenever(paymentRepository.saveAndFlush(payment)).thenReturn(payment)

        // when (이제 mockStatic 블록 없이 깔끔하게 호출합니다)
        paymentStatusService.finalizeRecordFromWebhook(payment, mockPayload)

        // then
        assertEquals(PaymentStatus.PAID, payment.status)
        assertEquals(expectedJson, payment.responseBody)
        verify(paymentRepository, times(1)).saveAndFlush(payment)
    }

    // =========================================================================
    // 예외/실패 케이스
    // =========================================================================

    @Test
    @DisplayName("시나리오 F2-1:결제 레코드가 존재하지 않는 경우 - PAYMENT_NOT_FOUND 예외가 발생한다")
    fun fail_F2_1_payment_not_found_throws_exception() {
        // given
        val payment = createPendingPayment()

        // 데이터베이스 비관적 락 조회 결과가 유실(null)된 상황 설정
        whenever(paymentRepository.findByIdForUpdate(payment.id)).thenReturn(null)

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentStatusService.finalizeRecord(payment, PaymentStatus.PAID, null)
        }

        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, exception.errorCode)
        verify(paymentRepository, never()).saveAndFlush(any())
    }

}
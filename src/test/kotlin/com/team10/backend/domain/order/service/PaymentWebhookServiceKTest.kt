package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import com.team10.backend.domain.order.dto.webhook.WebhookPayload
import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import kotlin.jvm.java

@ExtendWith(MockitoExtension::class)
class PaymentWebhookServiceKTest {

    @InjectMocks
    private lateinit var paymentWebhookService: PaymentWebhookService2

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var paymentUpdateService: PaymentUpdateService

    @Mock
    private lateinit var paymentStatusService: PaymentStatusService

    private val orderId = "ORD-2026-0517"
    private val paymentKey = "toss_payment_key_1234"

    //  실제 WebhookPayload 객체를 조립하는 헬퍼 메서드
    private fun createWebhookPayload(status: String): WebhookPayload {
        return WebhookPayload(
            eventType = "PAYMENT_STATUS_CHANGED",
            data = WebhookPayload.Data(
                paymentKey = paymentKey,
                orderId = orderId,
                status = status,
                totalAmount = 15000L
            )
        )
    }

    // =========================================================================
    // 최종 성공 및 정상 처리 시나리오 webhook_processing 메서드
    // =========================================================================

    @Test
    @DisplayName("시나리오 S3-1: 결제 완료 웹훅 처리 (DONE) - PENDING 상태에서 PAID로 전이 가드를 통과하고 주문 완료 원자적 서비스를 호출한다")
    fun success_S3_1_done_webhook_processing() {
        // given
        val payload = createWebhookPayload(status = "DONE")
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        // 가드 조건: PENDING -> PAID 전이는 가능하므로 currentStatus를 PENDING으로 설정
        whenever(mockPayment.status).thenReturn(PaymentStatus.PENDING)
        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // when
        paymentWebhookService.processWebhook(payload)

        // then
        // 멱등성 가드가 통과되어 단일 트랜잭션 성공 파이프라인이 정상 호출되었는지 검증
        verify(paymentUpdateService, times(1)).completeOrderAndFinalizeRecord(
            eq(orderId),
            eq(paymentKey),
            any<TossConfirmResponse>()
        )
        // 만료, 실패, 취소 등의 타 분기 로직은 절대로 수행되지 않아야 함
        verify(paymentUpdateService, never()).rollbackStockAndCancelOrder(any(), any(), any(), any())
        verify(paymentStatusService, never()).finalizeRecord(any(), any(), anyOrNull())
    }

    @Test
    @DisplayName("시나리오 S3-2: 결제 만료 웹훅 처리 (EXPIRED) - READY 상태에서 EXPIRED로 전이 가드를 통과하고 안전하게 재고를 롤백한다")
    fun success_S3_2_expired_webhook_processing() {
        // given
        val payload = createWebhookPayload(status = "EXPIRED")
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        //  READY -> EXPIRED 전이는 가능
        whenever(mockPayment.status).thenReturn(PaymentStatus.READY)
        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // when
        paymentWebhookService.processWebhook(payload)

        // then
        // 특수 만료 처리 분기를 타서 오름차순 비관락 재고 롤백이 올바른 인자들과 함께 실행되었는지 검증
        verify(paymentUpdateService, times(1)).rollbackStockAndCancelOrder(
            orderId = orderId,
            expired = "EXPIRED",
            nextStatus = PaymentStatus.EXPIRED,
            paymentKey = paymentKey
        )
        // Early Return이 정상 동작하여 하단의 일반 성공/실패 분기문(when)은 타지 않아야 함
        verify(paymentUpdateService, never()).completeOrderAndFinalizeRecord(any(), any(), any())
        verify(paymentStatusService, never()).finalizeRecord(any(), any(), anyOrNull())
    }

    @Test
    @DisplayName("시나리오 S3-3: 결제 실패 웹훅 처리 (ABORTED) - 사용자의 한도초과,카드번호 문제 등으로 실패 시 재고 복구 없이 FAILED로 확정 기록한다")
    fun success_S3_3_aborted_webhook_processing() {
        // given
        val payload = createWebhookPayload(status = "ABORTED")
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        // 가드 조건: PENDING -> FAILED 전이는 가능함
        whenever(mockPayment.status).thenReturn(PaymentStatus.PENDING)
        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // when
        paymentWebhookService.processWebhook(payload)

        // then
        // 재고를 건드리는 롤백 서비스 호출되지 않고, 오직 결제 레코드만 FAILED 상태로 전이
        verify(paymentStatusService, times(1)).finalizeRecord(
            record = mockPayment,
            status = PaymentStatus.FAILED,
            response = null
        )
        verify(paymentUpdateService, never()).completeOrderAndFinalizeRecord(any(), any(), any())
        verify(paymentUpdateService, never()).rollbackStockAndCancelOrder(any(), any(), any(), any())
    }

    @Test
    @DisplayName("시나리오 S3-4: 결제 취소 웹훅 처리 (CANCELED) - PAID 상태인 주문의 취소 웹훅 인입 시 CANCELED 상태로 최종 전이한다")
    fun success_S3_4_canceled_webhook_processing() {
        // given
        val payload = createWebhookPayload(status = "CANCELED")
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        // 결제 완료(PAID) 상태에서만 CANCELED 전이가 가능
        whenever(mockPayment.status).thenReturn(PaymentStatus.PAID)
        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // when
        paymentWebhookService.processWebhook(payload)

        // then
        //  CANCELED 상태 로직이 호출되었는지 검증
        verify(paymentStatusService, times(1)).finalizeRecord(
            record = mockPayment,
            status = PaymentStatus.CANCELED,
            response = null
        )
        verify(paymentUpdateService, never()).completeOrderAndFinalizeRecord(any(), any(), any())
        verify(paymentUpdateService, never()).rollbackStockAndCancelOrder(any(), any(), any(), any())
    }


    // =========================================================================
    //  데이터 불일치 및 유실 시나리오 (시스템 예외 발생 트랙)
    // =========================================================================

    @Test
    @DisplayName("시나리오 E3-1: 존재하지 않는 주문 ID - 웹훅의 orderId가 DB에 없으면 ORDER_NOT_FOUND 예외가 발생한다")
    fun exception_E3_1_order_not_found() {
        // given
        val payload = createWebhookPayload(status = "DONE")

        // 주문 저장소에서 레코드를 찾지 못하는 환경 구성
        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(null)

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentWebhookService.processWebhook(payload)
        }

        assertEquals(ErrorCode.ORDER_NOT_FOUND, exception.errorCode)
        // 이후 결제 조회나 상태 변경 서비스는 일절 호출되지 않아야 함
        verify(paymentRepository, never()).findFirstByOrderOrderByCreatedAtDesc(any())
        verifyNoInteractions(paymentUpdateService, paymentStatusService)
    }

    @Test
    @DisplayName("시나리오 E3-2: 연관 결제 시도 기록 누락 - 주문은 존재하나 결제 시도 이력이 없으면 PAYMENT_NOT_FOUND 예외가 발생한다")
    fun exception_E3_2_payment_not_found() {
        // given
        val payload = createWebhookPayload(status = "DONE")
        val mockOrder = mock(Order::class.java)

        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(mockOrder)
        // 결제 저장소에서 최종 시도 이력을 찾지 못하는 환경 구성
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(null)

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentWebhookService.processWebhook(payload)
        }

        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(paymentUpdateService, paymentStatusService)
    }

    // =========================================================================
    //  멱등성 가드 시나리오 (정상 수용 및 무시 트랙 - return)
    // =========================================================================

    @Test
    @DisplayName("시나리오 E3-3: [중복 DONE 차단] 이미 성공(PAID) 처리된 건에 대해 중복 웹훅 인입 시 예외 없이 즉시 return 탈출한다")
    fun exception_E3_3_duplicate_done_webhook_ignored() {
        // given
        val payload = createWebhookPayload(status = "DONE") // 변환 시 PaymentStatus.PAID
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        //  현재 내부 상태가 이미 PAID 상태임
        whenever(mockPayment.status).thenReturn(PaymentStatus.PAID)
        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // when
        // PAID.canTransitionTo(PAID)는 false를 반환하므로 내부 가드문에서 return됨
        paymentWebhookService.processWebhook(payload)

        // then
        // 예외는 던지지 않지만(토스에 200 OK), 중복 연산을 수행하는 하위 비즈니스 로직은 절대 타지 않아야 함
        verify(paymentUpdateService, never()).completeOrderAndFinalizeRecord(any(), any(), any())
        verifyNoInteractions(paymentStatusService)
    }

    @Test
    @DisplayName("시나리오 E3-4: [최종 상태 변경 불허] 이미 최종 상태(CANCELED/FAILED/EXPIRED)인 건에 재인입 시 즉시 return 탈출한다")
    fun exception_E3_4_final_status_webhook_ignored() {
        // given
        val payload = createWebhookPayload(status = "DONE")
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        //  현재 결제가 이미 최종 실패(FAILED) 상태임
        whenever(mockPayment.status).thenReturn(PaymentStatus.FAILED)
        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // when
        // FAILED.canTransitionTo(PAID)는 항상 false이므로 가드문 작동
        paymentWebhookService.processWebhook(payload)

        // then
        verify(paymentUpdateService, never()).completeOrderAndFinalizeRecord(any(), any(), any())
        verifyNoInteractions(paymentStatusService)
    }

    @Test
    @DisplayName("시나리오 E3-5: [순서 뒤바뀜 방지] 결제 성공(PAID) 완료 상태에 네트워크 지연으로 유실된 과거 상태(ABORTED) 인입 시 즉시 return 탈출한다")
    fun exception_E3_5_out_of_order_webhook_ignored() {
        // given
        val payload = createWebhookPayload(status = "ABORTED") // 변환 시 PaymentStatus.FAILED
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        // 가드 조건 세팅: 이미 성공(PAID)하여 배송 준비중인데 과거의 실패(ABORTED) 웹훅이 뒤늦게 도착함
        whenever(mockPayment.status).thenReturn(PaymentStatus.PAID)
        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // when
        // PAID.canTransitionTo(FAILED)는 완고하게 false를 반환하여 상태 역전을 막음
        paymentWebhookService.processWebhook(payload)

        // then
        // 이미 성공 처리된 데이터가 실패로 덮어써지거나 재고 변경 로직을 막았는지 검증
        verify(paymentStatusService, never()).finalizeRecord(any(), any(), anyOrNull())
        verify(paymentUpdateService, never()).rollbackStockAndCancelOrder(any(), any(), any(), any())
    }

    // =========================================================================
    // 특수/정의되지 않은 상태 분기 시나리오
    // =========================================================================

    @Test
    @DisplayName("시나리오 E3-6: 알 수 없는 토스 상태 코드 인입 (UNCERTAIN) - 정의되지 않은 값이 올 경우 UNCERTAIN으로 수용 후 가드 규칙을 판별한다")
    fun exception_E3_6_uncertain_status_processing() {
        // given
        // 토스 명세에 없는 임의의 정산 코드 대입
        val payload = createWebhookPayload(status = "PARTIAL_CANCELED")
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        // 가드 조건 세팅: 현재 READY 상태인데 UNCERTAIN 상태 코드가 인입됨
        // READY.canTransitionTo(UNCERTAIN) 규칙은 false입니다.
        whenever(mockPayment.status).thenReturn(PaymentStatus.READY)
        whenever(orderRepository.findByOrderNumber(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // when
        paymentWebhookService.processWebhook(payload)

        // then
        verify(paymentUpdateService, never()).completeOrderAndFinalizeRecord(any(), any(), any())
        verify(paymentUpdateService, never()).rollbackStockAndCancelOrder(any(), any(), any(), any())
        verifyNoInteractions(paymentStatusService)
    }

}
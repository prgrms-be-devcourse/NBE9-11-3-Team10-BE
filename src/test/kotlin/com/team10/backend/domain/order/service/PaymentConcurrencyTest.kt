package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.order.enums.DeliveryStatus
import com.team10.backend.domain.order.enums.OrderStatus
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.domain.order.enums.RequestType
import com.team10.backend.domain.order.repository.OrderDeliveryRepository
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.OrderDeliveryFixture
import com.team10.backend.fixture.OrderFixture
import com.team10.backend.fixture.PaymentFixture
import com.team10.backend.fixture.ProductFixture
import com.team10.backend.fixture.UserFixture
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import jakarta.persistence.LockModeType
import jakarta.transaction.Transactional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.jpa.repository.Lock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.ArgumentMatchers.any
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
// 만약 스레드 간의 타이밍 때문에 대기(Timeout)가 필요하다면 이것도 자주 씁니다.
import org.mockito.Mockito.timeout
@SpringBootTest(properties = [
    "SECRET_KEY=c3ByaW5nYm9vdHRlc3Rqd3RzZWNyZXRreXNhZmVhbG9uZ2Vub3VnaGZvcmhzMjU2YWxnb3JpdGht",
    "custom.jwt.secretKey=c3ByaW5nYm9vdHRlc3Rqd3RzZWNyZXRreXNhZmVhbG9uZ2Vub3VnaGZvcmhzMjU2YWxnb3JpdGht"
])
class PaymentConcurrencyTest {

    @Autowired lateinit var paymentStatusService: PaymentStatusService
    @Autowired lateinit var paymentRepository: PaymentRepository
    @MockitoSpyBean lateinit var orderRepository: OrderRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var productRepository: ProductRepository

    @Autowired private lateinit var paymentUpdateService: PaymentUpdateService
    @Autowired private lateinit var orderDeliveryRepository: OrderDeliveryRepository

    @Test
    @DisplayName("시나리오 F1-1(통합): 이미 다른 스레드가 결제를 진행 중인 경우(PENDING) - ALREADY_PROCESSED_PAYMENT 예외가 발생한다")
    fun fail_F1_1_real_concurrency_already_pending_blocks_request() {
        // 1️. Given:  실제 DB 데이터 구축
        val buyer = userRepository.save(UserFixture.create())
        val seller = userRepository.save(UserFixture.createWithSellerInfo())
        val product = productRepository.save(ProductFixture.createSelling(user = seller))

        // 실제 주문(Order) 엔티티 생성 및 저장
        val realOrder = orderRepository.save(
            OrderFixture.create(
                user = buyer,
                products = listOf(product to 1)
            )
        )

        // [핵심 설정] 선행 스레드가 이미 토스 결제창을 열었거나 승인 요청을 진행 중인 상황(PENDING)을 시뮬레이션
        // 기존에 Order 가 생성되면서 자동으로 생성된 READY 결제건 외에, PENDING 상태의 결제 레코드를 추가로 영속화.
        val pendingPayment = Payment.createPayment(
            order = realOrder,
            orderNumber = realOrder.orderNumber,
            amount = realOrder.totalAmount,
            idempotencyKey = "TOSS-IDEMPOTENCY-KEY-PENDING-2026", // 유니크 제약조건을 피하기 위해 명확한 키 지정
            type = RequestType.PAYMENT
        )
        pendingPayment.markAsPending() // 상태를 의도적으로 PENDING으로 변경
        paymentRepository.saveAndFlush(pendingPayment) // DB에 확실하게 반영

        // 멀티스레드 테스트 환경 설정 (이미 PENDING인 상태에서 뒤늦게 2개의 동시 요청)
        val threadCount = 2
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val businessExceptionCount = AtomicInteger(0)
        var capturedErrorCode: ErrorCode? = null

        // When: 이미 PENDING 레코드가 존재하는 상태에서 서비스 메서드 호출
        repeat(threadCount) {
            executorService.submit {
                try {
                    // 이미 DB에 PENDING 상태의 최신 결제 내역이 있으므로,
                    // 두 스레드 모두 createNewPayment()까지 가지 못하고 handleExistingPayment()의 PENDING 분기점에서 예외가 터져야 합니다.
                    paymentStatusService.getOrCreatePaymentAttempt(realOrder, RequestType.PAYMENT,"test_idempotency_key",)
                    successCount.incrementAndGet()
                } catch (e: BusinessException) {
                    businessExceptionCount.incrementAndGet()
                    capturedErrorCode = e.errorCode // 발생한 비즈니스 에러 코드 캡처
                } finally {
                    latch.countDown() // 스레드 작업 완료 알림
                }
            }
        }
        latch.await() // 모든 스레드의 작업이 끝날 때까지 대기


        //Then: 결과 검증 (진행 중인 결제가 이미 있으므로, 두 요청 모두 실패해야 함)
        // 이미 진행 중인 결제 처리가 있으므로 새로 저장에 성공하는 요청은 단 한 건도 없다
        assertEquals(0, successCount.get(), "이미 결제가 진행 중이므로 어떤 요청도 정상 저장되어서는 안 됩니다.")

        // 진입한 두 스레드 모두 예외 차단막에 걸려 실패해
        assertEquals(threadCount, businessExceptionCount.get(), "진입한 모든 동시 요청이 비즈니스 예외를 뱉어야 합니다.")

        // 차단되었을 때 반환된 에러 코드가 ALREADY_PROCESSED_PAYMENT 인지 확인.
        assertEquals(ErrorCode.ALREADY_PROCESSED_PAYMENT, capturedErrorCode, "에러 코드가 ALREADY_PROCESSED_PAYMENT 여야 합니다.")
    }


    @Test
    @DisplayName("시나리오 S2-3(통합): 사용자 결제 승인과 웹훅이 동시에 성공 처리를 시도해도, 비관적 락에 의해 한 번만 정산 처리가 된다")
    fun success_S2_3_real_concurrency_approval_and_webhook() {
        val buyer = userRepository.save(UserFixture.create())
        val seller = userRepository.save(UserFixture.createWithSellerInfo())
        val product = productRepository.save(ProductFixture.createSelling(user = seller))

        // 기본 PENDING 상태의 주문 및 배송, 결제 데이터 생성
        val realOrder = orderRepository.save(OrderFixture.create(user = buyer, products = listOf(product to 1)))

        val delivery = realOrder.delivery
            ?: throw IllegalStateException("주문에 배송 정보가 누락되었습니다.")

        paymentRepository.save(PaymentFixture.createReady(order = realOrder))

        val mockResponse =
            TossConfirmResponse(paymentKey = "toss_payment_key_2026", orderId = realOrder.orderNumber, status = "DONE")

        // 멀티스레드 환경 준비 (2개의 스레드가 완벽히 동시에 진입)
        val threadCount = 2
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val totalExecutionCount = AtomicInteger(0)
        val exceptionCount = AtomicInteger(0)

        // When: 두 스레드가 동시에 completeOrderAndFinalizeRecord 를 호출
        repeat(threadCount) {
            executorService.submit {
                try {
                    // 비관적 락이 없다면 두 스레드 모두 가드문을 통과해 두 번 성공 처리를 하겠지만,
                    // 락이 동작하므로 한 스레드가 완전히 커밋할 때까지 다른 스레드는 첫 줄에서 대기.
                    paymentUpdateService.completeOrderAndFinalizeRecord(realOrder.orderNumber, "toss_payment_key_2026", mockResponse)
                    totalExecutionCount.incrementAndGet()
                } catch (e: Exception) {
                    exceptionCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()

        // Then: 비관적 락과 가드문의 정합성 결합 검증
        // 1. 실행 카운트 검증 (가드문 정상 회차 수용)
        assertEquals(2, totalExecutionCount.get(), "두 스레드 모두 예외 없이 무사히 메서드를 종료했어야 합니다.")
        assertEquals(0, exceptionCount.get(), "가드문은 예외를 던지지 않으므로 예외 카운트는 0이어야 합니다.")

        // 2. 최종 DB에 반영된 주문 상태가 깔끔하게 SUCCESS인지 먼저 재검증
        val updatedOrder = orderRepository.findById(realOrder.id).orElseThrow()
        assertEquals(OrderStatus.SUCCESS, updatedOrder.status, "주문 상태는 최종적으로 SUCCESS여야 합니다.")

        // 3.
        // 서비스 코드 내부 로직(orderDeliveryRepository.findById(order.id))과 완벽하게 싱크를 맞춥니다.
        val updatedDelivery = orderDeliveryRepository.findById(updatedOrder.id)
            .orElseThrow { IllegalStateException("DB에 실제 배송 정보가 존재하지 않습니다.") }
        // 서비스가 성공시킨 READY 상태를 정확하게 읽어옵니다.
        assertEquals(DeliveryStatus.READY, updatedDelivery.status, "배송 상태가 정상적으로 배송 준비 중(READY)으로 전환되었어야 합니다.")

        // 4. 결제 영수증(Payment) 테이블 검증
        val updatedPayment = paymentRepository.findFirstByOrderOrderByCreatedAtDesc(updatedOrder)
            ?: throw IllegalStateException("결제 정보가 없습니다.")
        assertEquals(PaymentStatus.PAID, updatedPayment.status, "최종 결제 영수증 상태는 PAID여야 합니다.")
    }


}
package com.team10.backend.domain.order.service


import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.OrderDelivery
import com.team10.backend.domain.order.entity.OrderProducts
import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.order.enums.OrderStatus
import com.team10.backend.domain.order.repository.OrderDeliveryRepository
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import tools.jackson.databind.ObjectMapper
import java.util.*

@ExtendWith(MockitoExtension::class)
class PaymentUpdateServiceKTest {

    @InjectMocks
    private lateinit var paymentUpdateService: PaymentUpdateService

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var orderDeliveryRepository: OrderDeliveryRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var objectMapper: ObjectMapper

    // 테스트 공통 매개변수
    private val orderId = "ORD-2026-0517"
    private val paymentKey = "mock_payment_key_123"

    // 주문 상품 및 상품 관계를 가공해주는 헬퍼 메서드
    private fun createMockOrderProduct(productId: Long, quantity: Int): Pair<OrderProducts, Product> {
        val mockOrderProduct = mock(OrderProducts::class.java)
        val mockProduct = mock(Product::class.java)

        whenever(mockProduct.id).thenReturn(productId)
        whenever(mockOrderProduct.product).thenReturn(mockProduct)
        whenever(mockOrderProduct.quantity).thenReturn(quantity)

        return Pair(mockOrderProduct, mockProduct)
    }

    // =========================================================================
    //  성공 케이스
    // =========================================================================

    @Test
    @DisplayName("시나리오 S1-1: 최초 성공 처리 완료 - 모든 연관 엔티티의 상태가 성공으로 전이되고 정상 커밋된다")
    fun success_S1_1_complete_order_and_finalize_record() {
        // given
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)
        val mockDelivery = mock(OrderDelivery::class.java)
        val mockResponse = TossConfirmResponse(paymentKey = paymentKey, orderId = orderId, status = "DONE")
        val expectedJson = """{"status":"DONE"}"""

        whenever(mockOrder.status).thenReturn(OrderStatus.PENDING)
        whenever(mockOrder.id).thenReturn(1L)

        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)
        whenever(orderDeliveryRepository.findById(1L)).thenReturn(Optional.of(mockDelivery))
        whenever(objectMapper.writeValueAsString(mockResponse)).thenReturn(expectedJson)

        // when
        paymentUpdateService.completeOrderAndFinalizeRecord(orderId, paymentKey, mockResponse)

        // then
        verify(mockPayment, times(1)).completePayment(paymentKey)
        verify(mockPayment, times(1)).complete(expectedJson)
        verify(mockOrder, times(1)).successStatusOrder()
        verify(mockDelivery, times(1)).startReady()

        // 명시적 저장 호출 검증
        verify(paymentRepository, times(1)).save(mockPayment)
        verify(orderRepository, times(1)).save(mockOrder)
        verify(orderDeliveryRepository, times(1)).save(mockDelivery)
    }

    // =========================================================================
    //  동시성 및 예외 케이스
    // =========================================================================

    @Test
    @DisplayName("시나리오 E1-1: 중복 요청 발생 시 가드 작동 - 주문 상태가 이미 SUCCESS이면 추가 작업 없이 즉시 리턴한다")
    fun fail_E1_1_already_success_order_returns_immediately() {
        // given
        val mockOrder = mock(Order::class.java)
        val mockResponse = mock(TossConfirmResponse::class.java)

        whenever(mockOrder.status).thenReturn(OrderStatus.SUCCESS) // 이미 성공한 상태 가설
        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(mockOrder)

        // when
        paymentUpdateService.completeOrderAndFinalizeRecord(orderId, paymentKey, mockResponse)

        // then
        // 가드에 걸려 즉시 리턴했으므로 하위 엔티티 조회 및 비즈니스 로직이 전혀 호출되지 않아야 함
        verify(paymentRepository, never()).findFirstByOrderOrderByCreatedAtDesc(any())
        verify(orderDeliveryRepository, never()).findById(any())
        verify(paymentRepository, never()).save(any())
        verify(orderRepository, never()).save(any())
    }

    @Test
    @DisplayName("시나리오 E1-2: 주문 레코드 누락 - ORDER_NOT_FOUND 예외가 발생한다")
    fun fail_E1_2_order_not_found_throws_exception() {
        // given
        val mockResponse = mock(TossConfirmResponse::class.java)
        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(null)

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentUpdateService.completeOrderAndFinalizeRecord(orderId, paymentKey, mockResponse)
        }

        assertEquals(ErrorCode.ORDER_NOT_FOUND, exception.errorCode)
        verify(paymentRepository, never()).save(any())
    }

    @Test
    @DisplayName("시나리오 E1-3: 결제 시도 기록 누락 - PAYMENT_NOT_FOUND 예외가 발생한다")
    fun fail_E1_3_payment_not_found_throws_exception() {
        // given
        val mockOrder = mock(Order::class.java)
        val mockResponse = mock(TossConfirmResponse::class.java)

        whenever(mockOrder.status).thenReturn(OrderStatus.PENDING)
        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(null) // 결제 기록 유실

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentUpdateService.completeOrderAndFinalizeRecord(orderId, paymentKey, mockResponse)
        }

        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, exception.errorCode)
        verify(orderRepository, never()).save(any())
    }

    @Test
    @DisplayName("시나리오 E1-4: 배송 정보 누락 - DELIVERY_NOT_FOUND 예외가 발생한다")
    fun fail_E1_4_delivery_not_found_throws_exception() {
        // given
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)
        val mockResponse = mock(TossConfirmResponse::class.java)

        whenever(mockOrder.status).thenReturn(OrderStatus.PENDING)
        whenever(mockOrder.id).thenReturn(1L)

        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)
        whenever(orderDeliveryRepository.findById(1L)).thenReturn(Optional.empty()) // 배송 정보 없음

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentUpdateService.completeOrderAndFinalizeRecord(orderId, paymentKey, mockResponse)
        }

        assertEquals(ErrorCode.DELIVERY_NOT_FOUND, exception.errorCode)
        verify(orderDeliveryRepository, never()).save(any())
    }

    @Test
    @DisplayName("시나리오 E1-5: 영수증 직렬화 에러 발생 시 예외가 전파된다 (트랜잭션 롤백 유도)")
    fun fail_E1_5_serialization_error_propagates_exception() {
        // given
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)
        val mockDelivery = mock(OrderDelivery::class.java)
        val mockResponse = mock(TossConfirmResponse::class.java)

        whenever(mockOrder.status).thenReturn(OrderStatus.PENDING)
        whenever(mockOrder.id).thenReturn(1L)

        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)
        whenever(orderDeliveryRepository.findById(1L)).thenReturn(Optional.of(mockDelivery))

        // Jackson 직렬화 중 런타임 에러 강제 발생
        whenever(objectMapper.writeValueAsString(mockResponse)).thenThrow(RuntimeException("Jackson Error"))

        // when & then
        assertThrows(RuntimeException::class.java) {
            paymentUpdateService.completeOrderAndFinalizeRecord(orderId, paymentKey, mockResponse)
        }

        // 상태 변경 및 최종 저장 메서드들이 절대 실행되지 않아야 함
        verify(mockOrder, never()).successStatusOrder()
        verify(orderRepository, never()).save(any())
    }


    // =========================================================================
    //  성공 케이스 rollbackStockAndCancelOrder 메서드
    // =========================================================================

    @Test
    @DisplayName("시나리오 S2-1: 정상적인 재고 복구 및 주문 만료 처리 - 상품 ID 오름차순으로 원자적 UPDATE를 실행한다")
    fun success_S2_1_rollback_stock_and_cancel_order() {
        // given
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        whenever(mockOrder.status).thenReturn(OrderStatus.PENDING)
        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // 재고 복구 UPDATE 호출 순서 검증을 위해 ID를 역순(2L, 1L)으로 리스트 생성
        val (op2, p2) = createMockOrderProduct(productId = 2L, quantity = 3)
        val (op1, p1) = createMockOrderProduct(productId = 1L, quantity = 5)
        whenever(mockOrder.orderProducts).thenReturn(listOf(op2, op1) as MutableList<OrderProducts>?)

        // Repository 재고 복구 스텁 설정 (ID 순서대로 업데이트될 예정)
        whenever(productRepository.increaseStockAtomically(1L, 5)).thenReturn(1)
        whenever(productRepository.increaseStockAtomically(2L, 3)).thenReturn(1)

        // when
        paymentUpdateService.rollbackStockAndCancelOrder(
            orderId = orderId,
            expired = "EXPIRED",
            nextStatus = null,
            paymentKey = null
        )

        // then
        // 1. 재고 복구 UPDATE가 상품 ID 오름차순으로 호출되었는지 검증
        val inOrder = inOrder(productRepository)
        inOrder.verify(productRepository).increaseStockAtomically(1L, 5)
        inOrder.verify(productRepository).increaseStockAtomically(2L, 3)

        // 2. 엔티티 메서드가 아닌 원자적 UPDATE로 재고 복구했는지 검증
        verify(p1, never()).increaseStock(any())
        verify(p2, never()).increaseStock(any())

        // 3. 주문 및 결제 상태 만료 처리 확인
        verify(mockPayment, times(1)).expirePayment()
        verify(mockOrder, times(1)).expireStatusOrder()
    }

    // =========================================================================
    //  동시성 및 예외 케이스 rollbackStockAndCancelOrder 메서드
    // =========================================================================

    @Test
    @DisplayName("시나리오 E2-2: 중복 만료요청 가드 작동 - 주문 상태가 이미 EXPIRED이면 재고를 복구하지 않고 즉시 리턴한다")
    fun fail_E2_2_already_expired_order_returns_immediately() {
        // given
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        whenever(mockOrder.status).thenReturn(OrderStatus.EXPIRED) // 이미 만료된 주문 상태 연출
        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        // when
        paymentUpdateService.rollbackStockAndCancelOrder(
            orderId = orderId,
            expired = "EXPIRED",
            nextStatus = null,
            paymentKey = null
        )

        // then
        // 가드에 막혀 즉시 탈출했으므로 상품 조회 및 재고 증가 처리가 전혀 실행되지 않아야 함
        verify(productRepository, never()).findByIdWithPessimisticLock(any())
        verify(mockPayment, never()).expirePayment()
        verify(mockOrder, never()).expireStatusOrder()
    }

    @Test
    @DisplayName("시나리오 E2-3-1: 주문 레코드 누락 - ORDER_NOT_FOUND 예외가 발생한다")
    fun fail_E2_3_1_order_not_found_throws_exception() {
        // given
        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(null)

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentUpdateService.rollbackStockAndCancelOrder(orderId, "EXPIRED", null, null)
        }

        assertEquals(ErrorCode.ORDER_NOT_FOUND, exception.errorCode)
        verify(productRepository, never()).findByIdWithPessimisticLock(any())
    }

    @Test
    @DisplayName("시나리오 E2-3-2: 결제 시도 기록 누락 - PAYMENT_NOT_FOUND 예외가 발생한다")
    fun fail_E2_3_2_payment_not_found_throws_exception() {
        // given
        val mockOrder = mock(Order::class.java)
        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(null) // 결제 누락

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentUpdateService.rollbackStockAndCancelOrder(orderId, "EXPIRED", null, null)
        }

        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, exception.errorCode)
    }

    @Test
    @DisplayName("시나리오 E2-4: 상품 레코드 누락 - PRODUCT_NOT_FOUND 예외가 발생하고 트랜잭션이 중단된다")
    fun fail_E2_4_product_not_found_throws_exception() {
        // given
        val mockOrder = mock(Order::class.java)
        val mockPayment = mock(Payment::class.java)

        whenever(mockOrder.status).thenReturn(OrderStatus.PENDING)
        whenever(orderRepository.findByOrderNumberWithPessimisticLock(orderId)).thenReturn(mockOrder)
        whenever(paymentRepository.findFirstByOrderOrderByCreatedAtDesc(mockOrder)).thenReturn(mockPayment)

        val (op1, _) = createMockOrderProduct(productId = 1L, quantity = 5)

        lenient().whenever(op1.quantity).thenReturn(5)

        whenever(mockOrder.orderProducts).thenReturn(listOf(op1) as MutableList<OrderProducts>?)

        // 원자적 재고 복구 UPDATE 실패 상황 시뮬레이션
        whenever(productRepository.increaseStockAtomically(1L, 5)).thenReturn(0)

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            paymentUpdateService.rollbackStockAndCancelOrder(orderId, "EXPIRED", null, null)
        }

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.errorCode)
        // 후속 로직인 상태 변경 코드를 타지 않았는지 체크
        verify(mockPayment, never()).expirePayment()
        verify(mockOrder, never()).expireStatusOrder()
    }
}
package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.ConfirmRequest
import com.team10.backend.domain.order.repository.OrderDeliveryRepository
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.fixture.OrderFixture
import com.team10.backend.fixture.PaymentFixture
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(MockKExtension::class)
class PaymentCheckServiceKTest {

    @MockK
    lateinit var paymentRepository: PaymentRepository


    @MockK
    lateinit var orderRepository: OrderRepository

    @MockK
    lateinit var orderDeliveryRepository: OrderDeliveryRepository

    @InjectMockKs
    lateinit var paymentCheckService: PaymentCheckService

    @Nested
    @DisplayName("결제 요청 사전 검증 테스트")
    inner class ValidatePaymentRequest {

        private val orderNumber = "ORD-20240321-ABC1234"
        private val amount = 15000L
        private val request = ConfirmRequest(
            paymentKey = "test_pk_123",
            orderId = orderNumber,
            amount = amount
        )

        @Test
        @DisplayName("성공: 모든 정보가 일치하면 검증을 통과한다")
        fun success_validate_payment_request() {
            // given
            val order = OrderFixture.create(orderNumber = orderNumber)
            val payment = PaymentFixture.createReady(order = order, amount = amount.toInt())

            every { paymentRepository.findByOrderNumber(orderNumber) } returns payment
            every { orderRepository.findByOrderNumber(orderNumber) } returns order
            every { orderDeliveryRepository.existsById(any()) } returns true

            // when & then (예외가 발생하지 않아야 함)
            paymentCheckService.validatePaymentRequest(request)

            verify(exactly = 1) { paymentRepository.findByOrderNumber(orderNumber) }
            verify(exactly = 1) { orderRepository.findByOrderNumber(orderNumber) }
            verify(exactly = 1) { orderDeliveryRepository.existsById(any()) }
        }

        @Test
        @DisplayName("실패: 결제 정보가 존재하지 않으면 PAYMENT_NOT_FOUND 예외가 발생한다")
        fun fail_payment_not_found() {
            // given
            every { paymentRepository.findByOrderNumber(orderNumber) } returns null

            // when
            val exception = assertThrows<BusinessException> {
                paymentCheckService.validatePaymentRequest(request)
            }

            // then
            assertEquals(ErrorCode.PAYMENT_NOT_FOUND, exception.errorCode)
        }

        @Test
        @DisplayName("실패: 주문 정보가 존재하지 않으면 ORDER_NOT_FOUND 예외가 발생한다")
        fun fail_order_not_found() {
            // given
            val payment = PaymentFixture.createReady(orderNumber = orderNumber)

            every { paymentRepository.findByOrderNumber(orderNumber) } returns payment
            every { orderRepository.findByOrderNumber(orderNumber) } returns null

            // when
            val exception = assertThrows<BusinessException> {
                paymentCheckService.validatePaymentRequest(request)
            }

            // then
            assertEquals(ErrorCode.ORDER_NOT_FOUND, exception.errorCode)
        }

        @Test
        @DisplayName("실패: 배송 정보가 존재하지 않으면 DELIVERY_NOT_FOUND 예외가 발생한다")
        fun fail_delivery_not_found() {
            // given
            val order = OrderFixture.create(orderNumber = orderNumber)
            val payment = PaymentFixture.createReady(order = order)

            every { paymentRepository.findByOrderNumber(orderNumber) } returns payment
            every { orderRepository.findByOrderNumber(orderNumber) } returns order
            every { orderDeliveryRepository.existsById(any()) } returns false // 배송 정보 없음

            // when
            val exception = assertThrows<BusinessException> {
                paymentCheckService.validatePaymentRequest(request)
            }

            // then
            assertEquals(ErrorCode.DELIVERY_NOT_FOUND, exception.errorCode)
        }

        @Test
        @DisplayName("실패: 요청 금액과 DB의 결제 금액이 다르면 AMOUNT_MISMATCH 예외가 발생한다")
        fun fail_amount_mismatch() {
            // given
            val dbAmount = 20000 // DB에는 2만원
            val requestAmount = 15000L // 요청은 1.5만원 (위조 시나리오)

            val order = OrderFixture.create(orderNumber = orderNumber)
            val payment = PaymentFixture.createReady(order = order, amount = dbAmount)
            val mismatchRequest = ConfirmRequest("pk", orderNumber, requestAmount)

            every { paymentRepository.findByOrderNumber(orderNumber) } returns payment
            every { orderRepository.findByOrderNumber(orderNumber) } returns order
            every { orderDeliveryRepository.existsById(any()) } returns true

            // when
            val exception = assertThrows<BusinessException> {
                paymentCheckService.validatePaymentRequest(mismatchRequest)
            }

            // then
            assertEquals(ErrorCode.AMOUNT_MISMATCH, exception.errorCode)
        }
    }
}
package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.ConfirmRequest
import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.enums.RequestType
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.fixture.OrderFixture
import com.team10.backend.fixture.PaymentFixture
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets

@SpringBootTest
@ActiveProfiles("test")
class OrderConfirmServiceKTest {

    @Autowired
    private lateinit var orderConfirmService: OrderConfirmService

    @MockitoBean
    private lateinit var restTemplate: RestTemplate

    @MockitoBean
    private lateinit var paymentStatusService: PaymentStatusService

    @MockitoBean
    private lateinit var orderRepository: OrderRepository

    private val orderNumber = "ORD-SUCCESS-100"
    private val amount = 15000L
    private val request = ConfirmRequest(paymentKey = "test_pk_123", orderId = orderNumber, amount = amount)

    @Test
    @DisplayName("성공: 이미 PAID 상태인 결제 시도가 존재하면 토스 API를 호출하지 않고 기존 데이터 조회를 통해 복원 및 반환한다")
    fun success_return_cached_payment_when_already_paid() {
        // given
        val order = OrderFixture.create(orderNumber = orderNumber)
        val jsonResponseBody = """{"paymentKey":"test_pk_123","orderId":"$orderNumber","status":"DONE"}"""
        val mockPayment = PaymentFixture.createPaid(order = order, amount = amount.toInt(), responseBody = jsonResponseBody)
        val expectedResponse = TossConfirmResponse(paymentKey = "test_pk_123", orderId = orderNumber, status = "DONE")

        whenever(orderRepository.findByOrderNumber(anyString())).thenReturn(order)
        whenever(paymentStatusService.getOrCreatePaymentAttempt(any(), any())).thenReturn(mockPayment)
        whenever(paymentStatusService.parseResponse(jsonResponseBody)).thenReturn(expectedResponse)

        // when
        val result = orderConfirmService.sendConfirmRequest(request, null)

        // then
        assertEquals(expectedResponse, result)
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(TossConfirmResponse::class.java))
    }

    @ParameterizedTest
    @ValueSource(strings = ["NOT_FOUND_PAYMENT", "NOT_FOUND_PAYMENT_SESSION"])
    @DisplayName("실패: 토스 API 404 에러 그룹 발생 시 내부 기록을 FAILED로 확정하고 전용 비즈니스 예외를 던진다")
    fun fail_notFoundGroup(errorCode: String) {
        // given
        val order = OrderFixture.create(orderNumber = orderNumber)
        val mockPayment = PaymentFixture.createReady(order = order)
        val errorResponseBody = """{"code":"$errorCode", "message":"Toss 404 Error"}"""

        val notFoundException = HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
            errorResponseBody.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        )

        whenever(orderRepository.findByOrderNumber(anyString())).thenReturn(order)
        whenever(paymentStatusService.getOrCreatePaymentAttempt(any(), any())).thenReturn(mockPayment)
        whenever(restTemplate.postForEntity(anyString(), any(), eq(TossConfirmResponse::class.java))).thenThrow(notFoundException)

        // when & then
        //  1 ExhaustedRetryException
        val exhaustedException = assertThrows(org.springframework.retry.ExhaustedRetryException::class.java) {
            orderConfirmService.sendConfirmRequest(request, null)
        }

        //  2  내부 cause(BusinessException)를 확인
        val businessException = exhaustedException.cause as? BusinessException
            ?: throw AssertionError("Expected BusinessException as the cause, but was ${exhaustedException.cause}")

        // 3 추출한 내부 비즈니스 예외의 에러 코드를 검증
        assertEquals(errorCode, businessException.errorCode.name)

        verify(paymentStatusService, times(1)).finalizeRecord(eq(mockPayment), eq(PaymentStatus.FAILED), eq(null))
    }

    @ParameterizedTest
    @ValueSource(strings = ["REJECT_ACCOUNT_PAYMENT", "REJECT_CARD_PAYMENT", "REJECT_CARD_COMPANY", "FORBIDDEN_REQUEST", "INVALID_PASSWORD"])
    @DisplayName("실패: 토스 API 403 에러 그룹 발생 시 내부 기록을 FAILED로 확정하고 전용 비즈니스 예외를 던진다")
    fun fail_forbiddenGroup(errorCode: String) {
        // given
        val order = OrderFixture.create(orderNumber = orderNumber)
        val mockPayment = PaymentFixture.createReady(order = order)
        val errorResponseBody = """{"code":"$errorCode", "message":"Toss 403 Error"}"""

        val forbiddenException = HttpClientErrorException.create(
            HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY,
            errorResponseBody.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        )

        whenever(orderRepository.findByOrderNumber(anyString())).thenReturn(order)
        whenever(paymentStatusService.getOrCreatePaymentAttempt(any(), any())).thenReturn(mockPayment)
        whenever(restTemplate.postForEntity(anyString(), any(), eq(TossConfirmResponse::class.java))).thenThrow(forbiddenException)

        // when & then
        val exhaustedException = assertThrows(org.springframework.retry.ExhaustedRetryException::class.java) {
            orderConfirmService.sendConfirmRequest(request, null)
        }

        val businessException = exhaustedException.cause as? BusinessException
            ?: throw AssertionError("Expected BusinessException as the cause, but was ${exhaustedException.cause}")

        assertEquals(errorCode, businessException.errorCode.name)

        verify(paymentStatusService, times(1)).finalizeRecord(eq(mockPayment), eq(PaymentStatus.FAILED), eq(null))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "ALREADY_PROCESSED_PAYMENT", "INVALID_REQUEST", "INVALID_API_KEY", "INVALID_REJECT_CARD",
        "INVALID_CARD_EXPIRATION", "INVALID_STOPPED_CARD", "INVALID_CARD_LOST_OR_STOLEN",
        "INVALID_CARD_NUMBER", "INVALID_ACCOUNT_INFO_RE_REGISTER", "UNAPPROVED_ORDER_ID"
    ])
    @DisplayName("실패: 토스 API 400 에러 그룹 발생 시 내부 기록을 FAILED로 확정하고 전용 비즈니스 예외를 던진다")
    fun fail_badRequestGroup(errorCode: String) {
        // given
        val order = OrderFixture.create(orderNumber = orderNumber)
        val mockPayment = PaymentFixture.createReady(order = order)
        val errorResponseBody = """{"code":"$errorCode", "message":"Toss 400 Error"}"""

        val badRequestException = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
            errorResponseBody.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        )

        whenever(orderRepository.findByOrderNumber(anyString())).thenReturn(order)
        whenever(paymentStatusService.getOrCreatePaymentAttempt(any(), any())).thenReturn(mockPayment)
        whenever(restTemplate.postForEntity(anyString(), any(), eq(TossConfirmResponse::class.java))).thenThrow(badRequestException)

        // when & then
        val exhaustedException = assertThrows(org.springframework.retry.ExhaustedRetryException::class.java) {
            orderConfirmService.sendConfirmRequest(request, null)
        }

        val businessException = exhaustedException.cause as? BusinessException
            ?: throw AssertionError("Expected BusinessException as the cause, but was ${exhaustedException.cause}")

        assertEquals(errorCode, businessException.errorCode.name)

        verify(paymentStatusService, times(1)).finalizeRecord(eq(mockPayment), eq(PaymentStatus.FAILED), eq(null))
    }

    @ParameterizedTest
    @ValueSource(strings = ["FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING", "UNKNOWN_PAYMENT_ERROR", "FAILED_INTERNAL_SYSTEM_PROCESSING"])
    @DisplayName("실패: 토스 API 500 에러 그룹 발생 시 내부 기록을 FAILED로 가두고 시스템 예외를 핸들링한다")
    fun fail_serverErrorGroup(errorCode: String) {
        // given
        val order = OrderFixture.create(orderNumber = orderNumber)
        val mockPayment = PaymentFixture.createReady(order = order)
        val errorResponseBody = """{"code":"$errorCode", "message":"Toss 500 Error"}"""

        val serverErrorException = HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY,
            errorResponseBody.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        )

        whenever(orderRepository.findByOrderNumber(anyString())).thenReturn(order)
        whenever(paymentStatusService.getOrCreatePaymentAttempt(any(), any())).thenReturn(mockPayment)
        whenever(restTemplate.postForEntity(anyString(), any(), eq(TossConfirmResponse::class.java))).thenThrow(serverErrorException)

        // when & then
        val exhaustedException = assertThrows(org.springframework.retry.ExhaustedRetryException::class.java) {
            orderConfirmService.sendConfirmRequest(request, null)
        }

        val businessException = exhaustedException.cause as? BusinessException
            ?: throw AssertionError("Expected BusinessException as the cause, but was ${exhaustedException.cause}")

        assertEquals(errorCode, businessException.errorCode.name)

        verify(paymentStatusService, times(1)).finalizeRecord(eq(mockPayment), eq(PaymentStatus.FAILED), eq(null))
    }

    @Test
    @DisplayName("네트워크 에러: API 호출 시 타임아웃 발생 시 3번 재시도하고 불확정(UNCERTAIN) 마킹 후 최종Recover 차단되어 예외 처리된다")
    fun retry_three_times_and_recover_final_fail() {
        // given
        val order = OrderFixture.create(orderNumber = orderNumber)
        val mockPayment = PaymentFixture.createReady(order = order)

        whenever(orderRepository.findByOrderNumber(anyString())).thenReturn(order)
        whenever(paymentStatusService.getOrCreatePaymentAttempt(any(), any())).thenReturn(mockPayment)

        // 매번 타임아웃 오류 발생 유도
        whenever(restTemplate.postForEntity(anyString(), any(), eq(TossConfirmResponse::class.java)))
            .thenThrow(ResourceAccessException("Network Timeout"))

        // when & then
        val exception = assertThrows(BusinessException::class.java) {
            orderConfirmService.sendConfirmRequest(request, null)
        }

        // then
        assertEquals(ErrorCode.NETWORK_ERROR_FINAL_FAILED, exception.errorCode)

        // 스프링 @Retryable 인프라 검증: API 호출이 최종 3번 수행되었는지 확인
        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(TossConfirmResponse::class.java))

        // 매 시도 실패 시 Uncached 기록을 UNCERTAIN으로 완화 마킹했는지 확인
        verify(paymentStatusService, times(3)).markRecordAsUncertain(eq(mockPayment))
    }
}